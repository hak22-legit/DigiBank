package com.bank.controller;

import com.bank.model.dto.AdminDTO;
import com.bank.model.dto.AuthenticatedUser;
import com.bank.model.dto.UserDTO;
import com.bank.service.AdminAuthService;
import com.bank.service.AuthService;
import com.bank.service.AuthenticationService;

/**
 * Controller facade for authentication and self-service recovery.
 * Provides unified login across all roles, customer-only registration,
 * and universal OTP password recovery.
 */
public class AuthController {
    private final AuthenticationService authenticationService;
    private final AuthService legacyAuthService;
    private final AdminAuthService legacyAdminAuthService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        this.legacyAuthService = null;
        this.legacyAdminAuthService = null;
    }

    public AuthController(AuthenticationService authenticationService, AuthService authService, AdminAuthService adminAuthService) {
        this.authenticationService = authenticationService;
        this.legacyAuthService = authService;
        this.legacyAdminAuthService = adminAuthService;
    }

    /**
     * Legacy constructor for backward compatibility with ControllerFactory tests.
     */
    public AuthController(AuthService authService, AdminAuthService adminAuthService) {
        this.authenticationService = null;
        this.legacyAuthService = authService;
        this.legacyAdminAuthService = adminAuthService;
    }

    /**
     * Unified login for Customers, Staff, and Admins.
     */
    public AuthenticatedUser login(String identifier, String password) {
        if (authenticationService != null) {
            return authenticationService.login(identifier, password);
        }
        UserDTO user = legacyAuthService.login(identifier, password);
        return AuthenticatedUser.fromCustomer(user);
    }

    /**
     * Registers a new customer account.
     */
    public UserDTO register(String username, String email, String password, String fullName, String phone) {
        return register(username, email, password, fullName, phone, com.bank.model.enums.Currency.USD);
    }

    public UserDTO register(String username, String email, String password, String fullName, String phone, com.bank.model.enums.Currency primaryCurrency) {
        if (authenticationService != null) {
            return authenticationService.register(username, email, password, fullName, phone, primaryCurrency);
        }
        return legacyAuthService.register(username, email, password, fullName, phone, primaryCurrency);
    }

    /**
     * Initiates universal password recovery by sending a 6-digit OTP code.
     */
    public String initiatePasswordRecovery(String email) {
        if (authenticationService != null) {
            return authenticationService.initiatePasswordRecovery(email);
        }
        throw new UnsupportedOperationException("Password recovery service not initialized");
    }

    /**
     * Verifies the 6-digit OTP code.
     */
    public boolean verifyRecoveryCode(String email, String code) {
        if (authenticationService != null) {
            return authenticationService.verifyRecoveryCode(email, code);
        }
        return false;
    }

    /**
     * Completes password reset with the verified recovery code.
     */
    public void resetPassword(String email, String code, String newPassword) {
        if (authenticationService != null) {
            authenticationService.resetPasswordWithCode(email, code, newPassword);
            return;
        }
        throw new UnsupportedOperationException("Password recovery service not initialized");
    }

    public AuthService.PasswordResetInitiationResult initiateCustomerPasswordReset(String identifier) {
        if (legacyAuthService != null) {
            return legacyAuthService.initiatePasswordReset(identifier);
        }
        throw new UnsupportedOperationException("AuthService not initialized");
    }

    public boolean verifyOtp(Long userId, String otpCode) {
        if (legacyAuthService != null) {
            return legacyAuthService.verifyOtp(userId, otpCode);
        }
        return false;
    }

    public int getRemainingOtpAttempts(Long userId) {
        if (legacyAuthService != null) {
            return legacyAuthService.getRemainingOtpAttempts(userId);
        }
        return 0;
    }

    public void resetPasswordWithOtp(Long userId, String otpCode, String newPassword, String confirmPassword) {
        if (legacyAuthService != null) {
            legacyAuthService.resetPasswordWithOtp(userId, otpCode, newPassword, confirmPassword);
            return;
        }
        throw new UnsupportedOperationException("AuthService not initialized");
    }

    public AuthService getAuthService() {
        return legacyAuthService;
    }

    /**
     * Unified logout clearing all user and staff/admin session data.
     */
    public void logout() {
        if (authenticationService != null) {
            authenticationService.logout();
        } else {
            if (legacyAuthService != null) legacyAuthService.logout();
            if (legacyAdminAuthService != null) legacyAdminAuthService.logout();
        }
    }

    public void logoutUser() {
        logout();
    }

    public void logoutAdmin() {
        logout();
    }

    @Deprecated
    public AdminDTO adminLogin(String username, String password) {
        if (legacyAdminAuthService != null) {
            return legacyAdminAuthService.login(username, password);
        }
        AuthenticatedUser auth = login(username, password);
        return auth.getAdminDTO();
    }
}
