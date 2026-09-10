package com.bank.service;

import com.bank.exception.AuthenticationException;
import com.bank.exception.DuplicateResourceException;
import com.bank.exception.InactiveAccountException;
import com.bank.exception.LockedAccountException;
import com.bank.model.dto.AuthenticatedUser;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Admin;
import com.bank.model.entity.User;
import com.bank.model.enums.AdminRole;
import com.bank.model.enums.AdminStatus;
import com.bank.model.enums.UserRole;
import com.bank.model.enums.UserStatus;
import com.bank.model.repository.AdminRepository;
import com.bank.model.repository.UserRepository;
import com.bank.security.PasswordHasher;
import com.bank.security.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private AuditLogService auditLogService;

    private AuthenticationService authService;

    private User sampleCustomer;
    private Admin sampleLoanOfficer;
    private Admin sampleSuperAdmin;

    @BeforeEach
    void setUp() {
        authService = new AuthenticationService(userRepository, adminRepository, accountService, auditLogService);
        SessionManager.logout();

        String rawPassword = "SecurePassword123!";
        String passwordHash = PasswordHasher.hash(rawPassword);

        sampleCustomer = User.builder()
                .userId(101L)
                .username("senghak")
                .email("senghak@digibank.local")
                .passwordHash(passwordHash)
                .fullName("Seng Hak")
                .phone("012345678")
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleLoanOfficer = Admin.builder()
                .adminId(201L)
                .username("loanofficer1")
                .email("loan@digibank.local")
                .passwordHash(passwordHash)
                .fullName("Sarah Loan Officer")
                .role(AdminRole.LOAN_OFFICER)
                .status(AdminStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleSuperAdmin = Admin.builder()
                .adminId(301L)
                .username("superadmin")
                .email("admin@digibank.local")
                .passwordHash(passwordHash)
                .fullName("System Administrator")
                .role(AdminRole.SUPER_ADMIN)
                .status(AdminStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Customer login by username returns AuthenticatedUser with CUSTOMER role")
    void testCustomerLoginSuccessByUsername() {
        when(userRepository.findByEmail("senghak")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("senghak")).thenReturn(Optional.of(sampleCustomer));
        when(adminRepository.findByEmail("senghak")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("senghak")).thenReturn(Optional.empty());

        AuthenticatedUser result = authService.login("senghak", "SecurePassword123!");

        assertNotNull(result);
        assertEquals(UserRole.CUSTOMER, result.getRole());
        assertEquals("senghak", result.getUsername());
        assertEquals("Seng Hak", result.getFullName());
        assertTrue(result.isCustomer());
        assertFalse(result.isStaff());
        assertFalse(result.isAdmin());
        assertTrue(SessionManager.isUserLoggedIn());
        assertEquals(sampleCustomer, SessionManager.getCurrentUser());
    }

    @Test
    @DisplayName("Customer login by email returns AuthenticatedUser with CUSTOMER role")
    void testCustomerLoginSuccessByEmail() {
        when(userRepository.findByEmail("senghak@digibank.local")).thenReturn(Optional.of(sampleCustomer));
        when(adminRepository.findByEmail("senghak@digibank.local")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("senghak@digibank.local")).thenReturn(Optional.empty());

        AuthenticatedUser result = authService.login("senghak@digibank.local", "SecurePassword123!");

        assertNotNull(result);
        assertEquals(UserRole.CUSTOMER, result.getRole());
        assertEquals("senghak@digibank.local", result.getEmail());
    }

    @Test
    @DisplayName("Staff (Loan Officer) login maps to STAFF role and registers audit log")
    void testStaffLoginSuccess() {
        when(userRepository.findByEmail("loanofficer1")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("loanofficer1")).thenReturn(Optional.empty());
        when(adminRepository.findByEmail("loanofficer1")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("loanofficer1")).thenReturn(Optional.of(sampleLoanOfficer));

        AuthenticatedUser result = authService.login("loanofficer1", "SecurePassword123!");

        assertNotNull(result);
        assertEquals(UserRole.STAFF, result.getRole());
        assertEquals("LOAN_OFFICER", result.getSpecificRole());
        assertTrue(result.isStaff());
        assertFalse(result.isCustomer());
        assertFalse(result.isAdmin());
        assertTrue(SessionManager.isAdminLoggedIn());
        verify(auditLogService).log(eq(201L), eq("LOGIN"), eq("admins"), eq(201L), contains("loanofficer1"));
    }

    @Test
    @DisplayName("Admin (Super Admin) login maps to ADMIN role and registers audit log")
    void testAdminLoginSuccess() {
        when(userRepository.findByEmail("superadmin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.empty());
        when(adminRepository.findByEmail("superadmin")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("superadmin")).thenReturn(Optional.of(sampleSuperAdmin));

        AuthenticatedUser result = authService.login("superadmin", "SecurePassword123!");

        assertNotNull(result);
        assertEquals(UserRole.ADMIN, result.getRole());
        assertEquals("SUPER_ADMIN", result.getSpecificRole());
        assertTrue(result.isAdmin());
        assertFalse(result.isCustomer());
        assertFalse(result.isStaff());
        assertTrue(SessionManager.isAdminLoggedIn());
        verify(auditLogService).log(eq(301L), eq("LOGIN"), eq("admins"), eq(301L), contains("superadmin"));
    }

    @Test
    @DisplayName("Failed login with wrong password produces generic error without leaking info")
    void testLoginFailureInvalidPassword() {
        when(userRepository.findByEmail("senghak")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("senghak")).thenReturn(Optional.of(sampleCustomer));
        when(adminRepository.findByEmail("senghak")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("senghak")).thenReturn(Optional.empty());

        AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                authService.login("senghak", "WrongPassword!")
        );

        assertEquals("Invalid email or password", ex.getMessage());
        assertFalse(SessionManager.isUserLoggedIn());
    }

    @Test
    @DisplayName("Failed login for non-existent user produces same generic error (prevents enumeration)")
    void testLoginFailureNonExistentUser() {
        when(userRepository.findByEmail("unknown@bank.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("unknown@bank.com")).thenReturn(Optional.empty());
        when(adminRepository.findByEmail("unknown@bank.com")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("unknown@bank.com")).thenReturn(Optional.empty());

        AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                authService.login("unknown@bank.com", "SomePassword123!")
        );

        assertEquals("Invalid email or password", ex.getMessage());
        assertFalse(SessionManager.isUserLoggedIn());
    }

    @Test
    @DisplayName("Login fails with InactiveAccountException when customer account status is INACTIVE")
    void testLoginFailureInactiveUser() {
        sampleCustomer.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByEmail("senghak")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("senghak")).thenReturn(Optional.of(sampleCustomer));
        when(adminRepository.findByEmail("senghak")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("senghak")).thenReturn(Optional.empty());

        assertThrows(InactiveAccountException.class, () ->
                authService.login("senghak", "SecurePassword123!")
        );
        assertFalse(SessionManager.isUserLoggedIn());
    }

    @Test
    @DisplayName("Login fails with LockedAccountException when customer account status is SUSPENDED")
    void testLoginFailureLockedUser() {
        sampleCustomer.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmail("senghak")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("senghak")).thenReturn(Optional.of(sampleCustomer));
        when(adminRepository.findByEmail("senghak")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("senghak")).thenReturn(Optional.empty());

        assertThrows(LockedAccountException.class, () ->
                authService.login("senghak", "SecurePassword123!")
        );
        assertFalse(SessionManager.isUserLoggedIn());
    }

    @Test
    @DisplayName("Registration strictly assigns Role = CUSTOMER and provisions checking account")
    void testRegistrationStrictlyAssignsCustomerRole() {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@bank.com")).thenReturn(Optional.empty());
        when(adminRepository.findByEmail("new@bank.com")).thenReturn(Optional.empty());

        User savedMock = User.builder()
                .userId(501L)
                .username("newuser")
                .email("new@bank.com")
                .fullName("New User")
                .phone("1234567")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedMock);

        UserDTO registered = authService.register("newuser", "new@bank.com", "Password123!", "New User", "1234567");

        assertNotNull(registered);
        assertEquals("newuser", registered.getUsername());
        assertEquals(UserStatus.ACTIVE, registered.getStatus());
        verify(userRepository).save(argThat(u -> u.getStatus() == UserStatus.ACTIVE));
        verify(accountService).createAccount(eq(savedMock), any(), any());
    }

    @Test
    @DisplayName("Registration rejects duplicate username across both user and admin directories")
    void testRegistrationRejectsDuplicateUsername() {
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("superadmin")).thenReturn(Optional.of(sampleSuperAdmin));

        assertThrows(DuplicateResourceException.class, () ->
                authService.register("superadmin", "different@bank.com", "Password123!", "Test", "123")
        );
    }

    @Test
    @DisplayName("Universal password recovery flow works for Customer")
    void testUniversalForgotPasswordCustomerFlow() {
        when(userRepository.findByEmail("senghak@digibank.local")).thenReturn(Optional.of(sampleCustomer));
        when(adminRepository.findByEmail("senghak@digibank.local")).thenReturn(Optional.empty());
        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleCustomer));

        String code = authService.initiatePasswordRecovery("senghak@digibank.local");
        assertNotNull(code);
        assertEquals(6, code.length());

        assertTrue(authService.verifyRecoveryCode("senghak@digibank.local", code));

        authService.resetPasswordWithCode("senghak@digibank.local", code, "BrandNewPassword123!");
        verify(userRepository).save(sampleCustomer);
    }

    @Test
    @DisplayName("Universal password recovery flow works for Staff/Admin and logs audit record")
    void testUniversalForgotPasswordStaffFlow() {
        when(userRepository.findByEmail("loan@digibank.local")).thenReturn(Optional.empty());
        when(adminRepository.findByEmail("loan@digibank.local")).thenReturn(Optional.of(sampleLoanOfficer));
        when(adminRepository.findById(201L)).thenReturn(Optional.of(sampleLoanOfficer));

        String code = authService.initiatePasswordRecovery("loan@digibank.local");
        assertNotNull(code);
        assertEquals(6, code.length());

        verify(auditLogService).log(eq(201L), eq("INITIATE_PASSWORD_RECOVERY"), eq("admins"), eq(201L), anyString());

        assertTrue(authService.verifyRecoveryCode("loan@digibank.local", code));

        authService.resetPasswordWithCode("loan@digibank.local", code, "NewStaffPassword123!");
        verify(adminRepository).save(sampleLoanOfficer);
        verify(auditLogService).log(eq(201L), eq("PASSWORD_RECOVERY"), eq("admins"), eq(201L), anyString());
    }

    @Test
    @DisplayName("Universal recovery fails with invalid code")
    void testUniversalForgotPasswordInvalidCode() {
        when(userRepository.findByEmail("senghak@digibank.local")).thenReturn(Optional.of(sampleCustomer));
        when(adminRepository.findByEmail("senghak@digibank.local")).thenReturn(Optional.empty());

        authService.initiatePasswordRecovery("senghak@digibank.local");

        assertFalse(authService.verifyRecoveryCode("senghak@digibank.local", "000000"));
        assertThrows(AuthenticationException.class, () ->
                authService.resetPasswordWithCode("senghak@digibank.local", "000000", "NewPass123!")
        );
    }

    @Test
    @DisplayName("Logout clears session and logs audit if admin was logged in")
    void testLogoutClearsSession() {
        SessionManager.loginAdmin(sampleSuperAdmin);
        assertTrue(SessionManager.isAdminLoggedIn());

        authService.logout();

        assertFalse(SessionManager.isAdminLoggedIn());
        assertFalse(SessionManager.isUserLoggedIn());
        verify(auditLogService).log(eq(301L), eq("LOGOUT"), eq("admins"), eq(301L), anyString());
    }
}
