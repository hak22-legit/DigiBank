package com.bank.service;

import com.bank.model.dto.UserDTO;
import com.bank.model.dto.UserMapper;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.model.enums.UserStatus;
import com.bank.exception.AuthenticationException;
import com.bank.exception.DuplicateResourceException;
import com.bank.model.entity.User;
import com.bank.model.repository.UserRepository;
import com.bank.security.PasswordHasher;
import com.bank.security.SessionManager;

import com.bank.model.entity.PasswordResetToken;
import com.bank.model.repository.PasswordResetRepository;
import com.bank.model.repository.PasswordResetRepositoryImpl;
import com.bank.security.PasswordValidator;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

public class AuthService {

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final PasswordResetRepository passwordResetRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public record PasswordResetInitiationResult(
            User user,
            String otpCode,
            LocalDateTime expiresAt,
            String maskedEmail
    ) {}

    public AuthService(UserRepository userRepository, AccountService accountService) {
        this(userRepository, accountService, new PasswordResetRepositoryImpl());
    }

    public AuthService(UserRepository userRepository, AccountService accountService,
                       PasswordResetRepository passwordResetRepository) {
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.passwordResetRepository = passwordResetRepository;
    }

    public UserDTO register(String username, String email, String password, String fullName, String phone) {
        return register(username, email, password, fullName, phone, Currency.USD);
    }

    public UserDTO register(String username, String email, String password, String fullName, String phone, Currency primaryCurrency) {
        com.bank.security.PasswordValidator.validate(password);

        if (userRepository.findByUsername(username).isPresent()) {
            throw new DuplicateResourceException("Username already taken: " + username);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateResourceException("Email already registered: " + email);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(PasswordHasher.hash(password))
                .fullName(fullName)
                .phone(phone)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        Currency cur = (primaryCurrency != null) ? primaryCurrency : Currency.USD;
        accountService.createAccount(savedUser, AccountType.CHECKING, cur);

        return UserMapper.toDTO(savedUser);
    }

    public UserDTO login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        if (!PasswordHasher.verify(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationException("Account is not active. Status: " + user.getStatus());
        }

        SessionManager.loginUser(user); // Session ទុក Entity ពេញលេញ សម្រាប់ Service ដទៃប្រើ ownership check
        return UserMapper.toDTO(user);  // ត្រឡប់ DTO ទៅ Console layer
    }

    public void logout() {
        SessionManager.logout();
    }

    public PasswordResetInitiationResult initiatePasswordReset(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new AuthenticationException("Account identifier is required");
        }

        String query = identifier.trim();
        Optional<User> userOpt = userRepository.findByUsername(query);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(query.toLowerCase());
        }

        if (userOpt.isEmpty()) {
            throw new AuthenticationException("No account registered with provided credentials.");
        }

        User user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationException("Account is not active. Status: " + user.getStatus());
        }

        // Generate cryptographically secure random 6-digit numeric string
        String otpCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(3);

        // Invalidate prior unused tokens for this user
        passwordResetRepository.invalidateAllForUser(user.getUserId());

        PasswordResetToken token = PasswordResetToken.builder()
                .userId(user.getUserId())
                .otpCode(otpCode)
                .attempts(0)
                .expiresAt(expiresAt)
                .isUsed(false)
                .build();

        passwordResetRepository.save(token);

        String maskedEmail = maskEmail(user.getEmail());
        return new PasswordResetInitiationResult(user, otpCode, expiresAt, maskedEmail);
    }

    public boolean verifyOtp(Long userId, String otpCode) {
        if (userId == null || otpCode == null) return false;

        Optional<PasswordResetToken> tokenOpt = passwordResetRepository.findLatestActiveToken(userId);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        PasswordResetToken token = tokenOpt.get();
        if (token.getAttempts() >= 3) {
            return false;
        }

        if (token.getOtpCode().equals(otpCode.trim())) {
            return true;
        } else {
            int newAttempts = token.getAttempts() + 1;
            token.setAttempts(newAttempts);
            passwordResetRepository.updateAttempts(token.getTokenId(), newAttempts);
            return false;
        }
    }

    public int getRemainingOtpAttempts(Long userId) {
        if (userId == null) return 0;
        Optional<PasswordResetToken> tokenOpt = passwordResetRepository.findLatestActiveToken(userId);
        if (tokenOpt.isEmpty()) return 0;
        return Math.max(0, 3 - tokenOpt.get().getAttempts());
    }

    public void resetPasswordWithOtp(Long userId, String otpCode, String newPassword, String confirmPassword) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new AuthenticationException("Passwords do not match");
        }

        PasswordValidator.validate(newPassword);

        Optional<PasswordResetToken> tokenOpt = passwordResetRepository.findByUserIdAndOtp(userId, otpCode.trim());
        if (tokenOpt.isEmpty()) {
            throw new AuthenticationException("Invalid or expired OTP code.");
        }

        PasswordResetToken token = tokenOpt.get();
        if (Boolean.TRUE.equals(token.getIsUsed())
                || token.getExpiresAt().isBefore(LocalDateTime.now())
                || (token.getAttempts() != null && token.getAttempts() >= 3)) {
            throw new AuthenticationException("Password reset token is no longer valid.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found: " + userId));

        user.setPasswordHash(PasswordHasher.hash(newPassword));
        userRepository.save(user);

        passwordResetRepository.markAsUsed(token.getTokenId());
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + "@" + domain;
    }
}