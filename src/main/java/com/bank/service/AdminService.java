package com.bank.service;

import com.bank.enums.AdminRole;
import com.bank.enums.AdminStatus;
import com.bank.exception.AdminNotFoundException;
import com.bank.exception.AuthenticationException;
import com.bank.exception.DuplicateResourceException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.*;
import com.bank.repository.*;
import com.bank.security.PasswordHasher;

import java.util.List;

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

    /**
     * SUPER_ADMIN oversight: view ALL fraud alerts regardless of status
     * (not just OPEN ones like COMPLIANCE_OFFICER's working queue).
     * Read-only - SUPER_ADMIN cannot investigate/resolve directly
     * (Separation of Duties).
     */
    public List<FraudAlert> getAllFraudAlerts(Admin superAdmin) {
        assertSuperAdmin(superAdmin);
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