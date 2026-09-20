package com.bank.service;

import com.bank.model.dto.*;
import com.bank.model.entity.Account;
import com.bank.model.entity.Admin;
import com.bank.model.entity.FraudAlert;
import com.bank.model.entity.User;
import com.bank.model.enums.*;
import com.bank.exception.AdminNotFoundException;
import com.bank.exception.DuplicateResourceException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.*;
import com.bank.model.repository.*;
import com.bank.security.PasswordHasher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class AdminService {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final AuditLogService auditLogService;

    public AdminService(AdminRepository adminRepository, UserRepository userRepository,
                        AccountRepository accountRepository, TransactionRepository transactionRepository,
                        FraudAlertRepository fraudAlertRepository, AuditLogService auditLogService) {
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.auditLogService = auditLogService;
    }

    public Admin createAdmin(Admin creator, String username, String email, String password,
                             String fullName, AdminRole role) {
        return createAdmin(creator, username, email, password, fullName, null, role);
    }

    public Admin createAdmin(Admin creator, String username, String email, String password,
                             String fullName, String phoneNumber, AdminRole role) {
        assertSuperAdmin(creator);

        if (adminRepository.findByUsername(username).isPresent()) {
            throw new DuplicateResourceException("Admin username already taken: " + username);
        }
        if (adminRepository.findByEmail(email).isPresent()) {
            throw new DuplicateResourceException("Admin email already registered: " + email);
        }

        Admin newAdmin = Admin.builder()
                .username(username)
                .email(email)
                .passwordHash(PasswordHasher.hash(password))
                .fullName(fullName)
                .phoneNumber(phoneNumber)
                .role(role)
                .status(AdminStatus.ACTIVE)
                .build();

        Admin saved = adminRepository.save(newAdmin);

        auditLogService.log(creator.getAdminId(), "CREATE_ADMIN", "admins", saved.getAdminId(),
                "Created new " + role + " admin: " + username);

        return saved;
    }

    public void resetAdminPassword(Admin superAdmin, Long targetAdminId, String newPassword) {
        assertSuperAdmin(superAdmin);

        Admin target = adminRepository.findById(targetAdminId)
                .orElseThrow(() -> new AdminNotFoundException("Admin not found: " + targetAdminId));

        target.setPasswordHash(PasswordHasher.hash(newPassword));
        adminRepository.save(target);

        auditLogService.log(superAdmin.getAdminId(), "RESET_ADMIN_PASSWORD", "admins", targetAdminId,
                "Password reset by SUPER_ADMIN for admin: " + target.getUsername());
    }

    public void resetAdminCredentials(Admin superAdmin, Long targetAdminId, String temporaryPassword,
                                      boolean requireChangeOnFirstLogin, boolean unlockAccount, String reason) {
        assertSuperAdmin(superAdmin);

        Admin target = adminRepository.findById(targetAdminId)
                .orElseThrow(() -> new AdminNotFoundException("Admin not found: " + targetAdminId));

        target.setPasswordHash(PasswordHasher.hash(temporaryPassword));
        if (unlockAccount) {
            target.setStatus(AdminStatus.ACTIVE);
        }
        adminRepository.save(target);

        String auditDesc = String.format("Staff credential reset by SUPER_ADMIN for admin: %s. Reason: %s. Unlock=%b, ForceChange=%b",
                target.getUsername(), (reason != null && !reason.isBlank() ? reason : "Staff reported forgotten credentials"),
                unlockAccount, requireChangeOnFirstLogin);
        auditLogService.log(superAdmin.getAdminId(), "STAFF_CREDENTIAL_OVERRIDE", "admins", targetAdminId, auditDesc);
    }

    public List<Admin> getAllAdmins(Admin requestingAdmin) {
        assertSuperAdmin(requestingAdmin);
        return adminRepository.findAll();
    }

    /**
     * SUPER_ADMIN oversight capability - view all customers in the system.
     * Cannot perform banking operations on their behalf, only view.
     */
    public List<User> getAllUsers(Admin requestingAdmin) {
        assertSuperAdmin(requestingAdmin);
        return userRepository.findAll();
    }

    public PagedResult<UserDirectoryItem> getUserDirectory(Admin superAdmin, int page, int pageSize) {
        assertSuperAdmin(superAdmin);
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;

        long total = userRepository.countUsers();
        int offset = (page - 1) * pageSize;
        List<UserDirectoryItem> items = userRepository.findUserDirectorySummary(offset, pageSize);
        int totalPages = (int) Math.ceil((double) total / pageSize);

        return PagedResult.<UserDirectoryItem>builder()
                .items(items)
                .currentPage(page)
                .pageSize(pageSize)
                .totalItems(total)
                .totalPages(Math.max(1, totalPages))
                .build();
    }

    public User toggleUserFreeze(Admin superAdmin, Long userId) {
        assertSuperAdmin(superAdmin);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UserStatus current = user.getStatus();
        UserStatus next = (current == UserStatus.SUSPENDED || current == UserStatus.FROZEN) ? UserStatus.ACTIVE : UserStatus.SUSPENDED;
        user.setStatus(next);
        User saved = userRepository.save(user);

        // Also synchronize status of linked accounts
        List<Account> accounts = accountRepository.findByUserId(userId);
        for (Account a : accounts) {
            a.setStatus(next == UserStatus.SUSPENDED ? AccountStatus.FROZEN : AccountStatus.ACTIVE);
            accountRepository.save(a);
        }

        String action = (next == UserStatus.SUSPENDED) ? "FREEZE_USER" : "UNFREEZE_USER";
        auditLogService.log(superAdmin.getAdminId(), action, "users", userId,
                "Super Admin toggled status to " + next + " for user: " + user.getUsername());

        return saved;
    }

    public UserProfileDossier getUserProfileDossier(Admin admin, Long userId) {
        if (admin.getRole() != AdminRole.SUPER_ADMIN && admin.getRole() != AdminRole.LOAN_OFFICER) {
            throw new UnauthorizedException("Only SUPER_ADMIN or LOAN_OFFICER can access user profile dossier");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<Account> accounts = accountRepository.findByUserId(userId);
        LocalDate regDate = user.getCreatedAt() != null ? user.getCreatedAt().toLocalDate() : LocalDate.now();

        return UserProfileDossier.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone() != null ? user.getPhone() : "-")
                .status(user.getStatus())
                .failedLoginAttempts(0)
                .registrationDate(regDate)
                .kycVerificationLevel("LEVEL_2 (FULL)")
                .securityMode("2FA ENABLED")
                .linkedAccounts(accounts)
                .build();
    }

    public String issueUserResetToken(Admin superAdmin, Long userId) {
        assertSuperAdmin(superAdmin);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String token = String.format("%06d", new java.util.Random().nextInt(900000) + 100000);
        auditLogService.log(superAdmin.getAdminId(), "ISSUE_RESET_TOKEN", "users", userId,
                "Issued temporary OTP reset token for user: " + user.getUsername());
        return token;
    }

    public PagedResult<GlobalLedgerItem> getGlobalLedger(Admin superAdmin, int page, int pageSize, String search, Currency currency) {
        assertSuperAdmin(superAdmin);
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;

        long total = accountRepository.countAccounts(search, currency);
        int offset = (page - 1) * pageSize;
        List<GlobalLedgerItem> items = accountRepository.findGlobalLedger(offset, pageSize, search, currency);
        int totalPages = (int) Math.ceil((double) total / pageSize);

        return PagedResult.<GlobalLedgerItem>builder()
                .items(items)
                .currentPage(page)
                .pageSize(pageSize)
                .totalItems(total)
                .totalPages(Math.max(1, totalPages))
                .build();
    }

    public Map<Currency, BigDecimal> getVaultTotals(Admin superAdmin) {
        assertSuperAdmin(superAdmin);
        return accountRepository.getVaultTotalAssets();
    }

    /**
     * Suspends an admin account (e.g. misconduct, leaving the company) -
     * different from resetting a password, this fully blocks login.
     */
    public void suspendAdmin(Admin superAdmin, Long targetAdminId) {
        assertSuperAdmin(superAdmin);

        Admin target = adminRepository.findById(targetAdminId)
                .orElseThrow(() -> new AdminNotFoundException("Admin not found: " + targetAdminId));

        if (target.getRole() == AdminRole.SUPER_ADMIN) {
            throw new UnauthorizedException("Cannot suspend another SUPER_ADMIN through this action");
        }

        target.setStatus(AdminStatus.INACTIVE);
        adminRepository.save(target);

        auditLogService.log(superAdmin.getAdminId(), "SUSPEND_ADMIN", "admins", targetAdminId,
                "Suspended admin: " + target.getUsername());
    }

    public void reactivateAdmin(Admin superAdmin, Long targetAdminId) {
        assertSuperAdmin(superAdmin);

        Admin target = adminRepository.findById(targetAdminId)
                .orElseThrow(() -> new AdminNotFoundException("Admin not found: " + targetAdminId));

        target.setStatus(AdminStatus.ACTIVE);
        adminRepository.save(target);

        auditLogService.log(superAdmin.getAdminId(), "REACTIVATE_ADMIN", "admins", targetAdminId,
                "Reactivated admin: " + target.getUsername());
    }

    public Admin toggleAdminRole(Admin superAdmin, Long targetAdminId) {
        assertSuperAdmin(superAdmin);

        Admin target = adminRepository.findById(targetAdminId)
                .orElseThrow(() -> new AdminNotFoundException("Admin not found: " + targetAdminId));

        if (target.getRole() == AdminRole.SUPER_ADMIN) {
            throw new UnauthorizedException("Cannot change role of SUPER_ADMIN");
        }

        AdminRole nextRole = (target.getRole() == AdminRole.LOAN_OFFICER)
                ? AdminRole.COMPLIANCE_OFFICER
                : AdminRole.LOAN_OFFICER;

        target.setRole(nextRole);
        Admin updated = adminRepository.save(target);

        auditLogService.log(superAdmin.getAdminId(), "TOGGLE_ADMIN_ROLE", "admins", targetAdminId,
                "Toggled role of admin " + target.getUsername() + " to " + nextRole);

        return updated;
    }

    /**
     * View all fraud alerts across the platform (SUPER_ADMIN and COMPLIANCE_OFFICER clearance).
     */
    public List<FraudAlert> getAllFraudAlerts(Admin admin) {
        if (admin.getRole() != AdminRole.SUPER_ADMIN && admin.getRole() != AdminRole.COMPLIANCE_OFFICER) {
            throw new com.bank.exception.AccessDeniedException("Access denied: Requires COMPLIANCE_OFFICER or SUPER_ADMIN role.");
        }
        return fraudAlertRepository.findAll();
    }

    public SystemStats getSystemStats(Admin superAdmin) {
        assertSuperAdmin(superAdmin);

        long openAlerts = fraudAlertRepository.findByStatus("OPEN").size();

        return SystemStats.builder()
                .totalUsers(userRepository.findAll().size())
                .totalAdmins(adminRepository.findAll().size())
                .totalAccounts(accountRepository.findAll().size())
                .totalTransactions(transactionRepository.findAll().size())
                .totalFraudAlerts(fraudAlertRepository.findAll().size())
                .openFraudAlerts(openAlerts)
                .build();
    }

    private void assertSuperAdmin(Admin admin) {
        if (admin.getRole() != AdminRole.SUPER_ADMIN) {
            throw new UnauthorizedException("Only SUPER_ADMIN can perform this action");
        }
    }
}