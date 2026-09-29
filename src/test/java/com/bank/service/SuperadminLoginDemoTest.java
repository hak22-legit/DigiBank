package com.bank.service;

import com.bank.controller.AuthController;
import com.bank.model.dto.AdminDTO;
import com.bank.model.dto.AuthenticatedUser;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Admin;
import com.bank.model.enums.AdminRole;
import com.bank.model.enums.AdminStatus;
import com.bank.model.enums.UserRole;
import com.bank.model.repository.AdminRepository;
import com.bank.model.repository.UserRepository;
import com.bank.security.PasswordHasher;
import com.bank.security.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperadminLoginDemoTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private AuditLogService auditLogService;

    private AuthenticationService authenticationService;
    private AuthService authService;
    private AdminAuthService adminAuthService;
    private AuthController authController;

    private Admin superAdminEntity;
    private static final String SEED_HASH = "$2a$12$85D9VpUlX/kGWjEfszWdeuECU8307jeMxS2mifHq/hExamkbtDeUm";

    @BeforeEach
    void setUp() {
        SessionManager.logout();

        superAdminEntity = Admin.builder()
                .adminId(1L)
                .username("superadmin")
                .email("superadmin@digibank.local")
                .passwordHash(SEED_HASH)
                .fullName("System Administrator")
                .role(AdminRole.SUPER_ADMIN)
                .status(AdminStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        authenticationService = new AuthenticationService(userRepository, adminRepository, accountService, auditLogService);
        authService = new AuthService(userRepository, accountService, null, adminRepository);
        adminAuthService = new AdminAuthService(adminRepository, auditLogService);
        authController = new AuthController(authenticationService, authService, adminAuthService);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("PasswordHasher: Demo password '1234' works for superadmin seed hash directly")
    void testPasswordHasherBypassWithSeedHash() {
        assertTrue(PasswordHasher.verify("1234", SEED_HASH),
                "PasswordHasher.verify('1234', SEED_HASH) should return true");
    }

    @Test
    @DisplayName("PasswordHasher: Demo password '1234' works for username 'superadmin'")
    void testPasswordHasherBypassWithUsername() {
        String customHash = PasswordHasher.hash("SomeOtherPassword");
        assertTrue(PasswordHasher.verify("superadmin", "1234", customHash),
                "PasswordHasher.verify('superadmin', '1234', hash) should return true");
    }

    @Test
    @DisplayName("PasswordHasher: Demo password '1234' fails for other users without matching hash")
    void testPasswordHasherBypassRejectsNonSuperadmin() {
        String customHash = PasswordHasher.hash("CustomerSecret!");
        assertFalse(PasswordHasher.verify("johndoe", "1234", customHash),
                "PasswordHasher.verify('johndoe', '1234', hash) must return false");
        assertFalse(PasswordHasher.verify("1234", customHash),
                "PasswordHasher.verify('1234', customHash) must return false");
    }

    @Test
    @DisplayName("PasswordHasher: Wrong password fails for superadmin")
    void testPasswordHasherRejectsWrongPasswordForSuperadmin() {
        assertFalse(PasswordHasher.verify("superadmin", "WrongPassword!", SEED_HASH),
                "PasswordHasher should reject wrong password for superadmin");
    }

    @Test
    @DisplayName("AuthenticationService: superadmin logs in with password '1234' and populates session")
    void testAuthenticationServiceSuperadminLoginWith1234() {
        when(adminRepository.findByEmail("superadmin")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("superadmin")).thenReturn(Optional.of(superAdminEntity));

        AuthenticatedUser result = authenticationService.login("superadmin", "1234");

        assertNotNull(result);
        assertEquals("superadmin", result.getUsername());
        assertEquals(UserRole.ADMIN, result.getRole());
        assertEquals("SUPER_ADMIN", result.getSpecificRole());
        assertTrue(result.isAdmin());
        assertFalse(result.isCustomer());
        assertFalse(result.isStaff());

        assertTrue(SessionManager.isAdminLoggedIn());
        assertEquals(superAdminEntity, SessionManager.getCurrentAdmin());
        verify(auditLogService).log(eq(1L), eq("LOGIN"), eq("admins"), eq(1L), any());
    }

    @Test
    @DisplayName("AuthController: login('superadmin', '1234') returns AuthenticatedUser with ADMIN role")
    void testAuthControllerSuperadminLoginWith1234() {
        when(adminRepository.findByEmail("superadmin")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("superadmin")).thenReturn(Optional.of(superAdminEntity));

        AuthenticatedUser result = authController.login("superadmin", "1234");

        assertNotNull(result);
        assertTrue(result.isAdmin());
        assertEquals(AdminRole.SUPER_ADMIN, result.getAdminDTO().getRole());
        assertTrue(SessionManager.isAdminLoggedIn());
    }

    @Test
    @DisplayName("AdminAuthService: login('superadmin', '1234') succeeds and creates session")
    void testAdminAuthServiceSuperadminLoginWith1234() {
        when(adminRepository.findByUsername("superadmin")).thenReturn(Optional.of(superAdminEntity));

        AdminDTO result = adminAuthService.login("superadmin", "1234");

        assertNotNull(result);
        assertEquals("superadmin", result.getUsername());
        assertEquals(AdminRole.SUPER_ADMIN, result.getRole());
        assertTrue(SessionManager.isAdminLoggedIn());
    }

    @Test
    @DisplayName("AuthService: login('superadmin', '1234') resolves from admins table and returns DTO")
    void testAuthServiceSuperadminLoginWith1234() {
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("superadmin")).thenReturn(Optional.of(superAdminEntity));

        UserDTO result = authService.login("superadmin", "1234");

        assertNotNull(result);
        assertEquals("superadmin", result.getUsername());
        assertTrue(SessionManager.isAdminLoggedIn());
    }
}
