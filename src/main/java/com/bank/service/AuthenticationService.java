package com.bank.service;

import com.bank.exception.AuthenticationException;
import com.bank.exception.DuplicateResourceException;
import com.bank.exception.InactiveAccountException;
import com.bank.exception.LockedAccountException;
import com.bank.model.dto.AdminDTO;
import com.bank.model.dto.AdminMapper;
import com.bank.model.dto.AuthenticatedUser;
import com.bank.model.dto.UserDTO;
import com.bank.model.dto.UserMapper;
import com.bank.model.entity.Admin;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.AdminStatus;
import com.bank.model.enums.Currency;
import com.bank.model.enums.UserStatus;
import com.bank.model.repository.AdminRepository;
import com.bank.model.repository.UserRepository;
import com.bank.security.PasswordHasher;
import com.bank.security.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise unified authentication and password recovery service.
 * Handles credential verification, anti-enumeration security, status validation,
 * role determination (CUSTOMER, STAFF, ADMIN), and universal OTP password recovery.
 */
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final String DUMMY_HASH = "$2a$12$e8YqJ0qjPz1d3eD3YgDkZeN7xQZ/YmHsmf.P87aLq5mG4jYw7v8h6";

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final AccountService accountService;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    // In-memory token cache for universal self-service recovery: email -> RecoveryToken
    private final Map<String, RecoveryToken> recoveryTokens = new ConcurrentHashMap<>();

    public AuthenticationService(UserRepository userRepository,
                                 AdminRepository adminRepository,
                                 AccountService accountService,
                                 AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.accountService = accountService;
        this.auditLogService = auditLogService;
    }

    /**
     * Unified authentication entry point. Accepts username or email and password.
     * Authenticates the user, validates account status, determines role, and initializes session.
     */
    public AuthenticatedUser login(String identifier, String password) {
        if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
            throw new AuthenticationException("Invalid email or password");
        }

        String normalizedId = identifier.trim();

        // 1. Search staff/admin repository first
        Optional<Admin> adminOpt = adminRepository.findByEmail(normalizedId)
                .or(() -> adminRepository.findByUsername(normalizedId));

        // 2. Search customer repository
        Optional<User> userOpt = userRepository.findByEmail(normalizedId)
                .or(() -> userRepository.findByUsername(normalizedId));

        // 3. Prevent account enumeration via timing attacks if identity not found
        if (adminOpt.isEmpty() && userOpt.isEmpty()) {
            PasswordHasher.verify(password, DUMMY_HASH);
            throw new AuthenticationException("Invalid email or password");
        }

        // 4. Authenticate Staff / Admin
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            if (PasswordHasher.verify(password, admin.getPasswordHash())) {
                validateAdminStatus(admin);
                SessionManager.loginAdmin(admin);
                auditLogService.log(admin.getAdminId(), "LOGIN", "admins", admin.getAdminId(),
                        "Staff/Admin logged in: " + admin.getUsername() + " [" + admin.getRole() + "]");
                AdminDTO adminDTO = AdminMapper.toDTO(admin);
                logger.info("Staff/Admin successfully authenticated: {} [{}]", admin.getUsername(), admin.getRole());
                return AuthenticatedUser.fromAdmin(adminDTO);
            }
        }

        // 5. Authenticate Customer
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (PasswordHasher.verify(password, user.getPasswordHash())) {
                validateUserStatus(user);
                SessionManager.loginUser(user);
                UserDTO userDTO = UserMapper.toDTO(user);
                logger.info("Customer successfully authenticated: {} ({})", user.getUsername(), user.getEmail());
                return AuthenticatedUser.fromCustomer(userDTO);
            }
        }

        // 6. Generic failure if password did not match either
        throw new AuthenticationException("Invalid email or password");
    }

    private void validateUserStatus(User user) {
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new InactiveAccountException("Your account is currently inactive. Please contact support.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.CLOSED) {
            throw new LockedAccountException("Your account is temporarily locked. Please contact support.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new LockedAccountException("Your account is temporarily locked. Please contact support.");
        }
    }

    private void validateAdminStatus(Admin admin) {
        if (admin.getStatus() != AdminStatus.ACTIVE) {
            throw new InactiveAccountException("Your account is currently inactive. Please contact support.");
        }
    }

    /**
     * Registers a new customer profile. Strictly enforces Role = CUSTOMER.
     * Prevents collision across both customer and staff/admin directories.
     */
    public UserDTO register(String username, String email, String password, String fullName, String phone) {
        String cleanUsername = username.trim();
        String cleanEmail = email.trim();

        if (userRepository.findByUsername(cleanUsername).isPresent() || adminRepository.findByUsername(cleanUsername).isPresent()) {
            throw new DuplicateResourceException("Username already taken: " + cleanUsername);
        }
        if (userRepository.findByEmail(cleanEmail).isPresent() || adminRepository.findByEmail(cleanEmail).isPresent()) {
            throw new DuplicateResourceException("Email already registered: " + cleanEmail);
        }

        User user = User.builder()
                .username(cleanUsername)
                .email(cleanEmail)
                .passwordHash(PasswordHasher.hash(password))
                .fullName(fullName.trim())
                .phone(phone != null ? phone.trim() : null)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        accountService.createAccount(savedUser, AccountType.CHECKING, Currency.USD);
        logger.info("Registered new customer account: {}", savedUser.getUsername());

        return UserMapper.toDTO(savedUser);
    }

    /**
     * Initiates universal self-service password recovery by generating a 6-digit OTP code.
     * Works for Customers, Staff, and Admins.
     */
    public String initiatePasswordRecovery(String email) {
        if (email == null || email.isBlank()) {
            throw new AuthenticationException("Email is required");
        }

        String normalizedEmail = email.trim().toLowerCase();

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        Optional<Admin> adminOpt = adminRepository.findByEmail(normalizedEmail);

        if (userOpt.isEmpty() && adminOpt.isEmpty()) {
            throw new AuthenticationException("No active account found associated with this email address.");
        }

        boolean isCustomer = userOpt.isPresent();
        Long accountId;

        if (isCustomer) {
            User user = userOpt.get();
            validateUserStatus(user);
            accountId = user.getUserId();
        } else {
            Admin admin = adminOpt.get();
            validateAdminStatus(admin);
            accountId = admin.getAdminId();
        }

        // Generate cryptographically secure 6-digit code
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(10));

        recoveryTokens.put(normalizedEmail, new RecoveryToken(normalizedEmail, code, expiresAt, 0, isCustomer, accountId));

        if (!isCustomer) {
            auditLogService.log(accountId, "INITIATE_PASSWORD_RECOVERY", "admins", accountId,
                    "Password recovery code generated for staff/admin email: " + normalizedEmail);
        }

        logger.info("Universal recovery code generated for {}: {}", normalizedEmail, code);
        return code;
    }

    /**
     * Verifies whether the submitted recovery code is valid and active.
     */
    public boolean verifyRecoveryCode(String email, String code) {
        if (email == null || code == null) return false;
        String key = email.trim().toLowerCase();
        RecoveryToken token = recoveryTokens.get(key);

        if (token == null || token.isExpired()) {
            recoveryTokens.remove(key);
            return false;
        }

        if (token.attempts >= 3) {
            recoveryTokens.remove(key);
            return false;
        }

        if (token.code.equals(code.trim())) {
            return true;
        } else {
            token.attempts++;
            return false;
        }
    }

    /**
     * Resets the password using a verified recovery code. Updates the appropriate table and invalidates the token.
     */
    public void resetPasswordWithCode(String email, String code, String newPassword) {
        if (!verifyRecoveryCode(email, code)) {
            throw new AuthenticationException("Invalid or expired recovery code.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }

        String key = email.trim().toLowerCase();
        RecoveryToken token = recoveryTokens.get(key);
        String newHash = PasswordHasher.hash(newPassword);

        if (token.isCustomer) {
            User user = userRepository.findById(token.accountId)
                    .orElseThrow(() -> new AuthenticationException("User account not found"));
            user.setPasswordHash(newHash);
            userRepository.save(user);
            logger.info("Customer password reset successfully for {}", user.getUsername());
        } else {
            Admin admin = adminRepository.findById(token.accountId)
                    .orElseThrow(() -> new AuthenticationException("Admin account not found"));
            admin.setPasswordHash(newHash);
            adminRepository.save(admin);
            auditLogService.log(admin.getAdminId(), "PASSWORD_RECOVERY", "admins", admin.getAdminId(),
                    "Password successfully reset via recovery code");
            logger.info("Staff/Admin password reset successfully for {}", admin.getUsername());
        }

        recoveryTokens.remove(key);
    }

    /**
     * Gracefully terminates user or staff/admin session.
     */
    public void logout() {
        Admin admin = SessionManager.getCurrentAdmin();
        if (admin != null) {
            auditLogService.log(admin.getAdminId(), "LOGOUT", "admins", admin.getAdminId(),
                    "Staff/Admin logged out: " + admin.getUsername());
        }
        SessionManager.logout();
    }

    private static class RecoveryToken {
        final String email;
        final String code;
        final Instant expiresAt;
        int attempts;
        final boolean isCustomer;
        final Long accountId;

        RecoveryToken(String email, String code, Instant expiresAt, int attempts, boolean isCustomer, Long accountId) {
            this.email = email;
            this.code = code;
            this.expiresAt = expiresAt;
            this.attempts = attempts;
            this.isCustomer = isCustomer;
            this.accountId = accountId;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
