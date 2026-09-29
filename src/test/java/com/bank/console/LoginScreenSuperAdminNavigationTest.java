package com.bank.console;

import com.bank.console.screens.CustomerDashboardScreen;
import com.bank.console.screens.LoginScreen;
import com.bank.console.screens.StaffDashboardScreen;
import com.bank.console.screens.SuperAdminDashboardScreen;
import com.bank.controller.AuthController;
import com.bank.model.dto.AdminDTO;
import com.bank.model.dto.AuthenticatedUser;
import com.bank.model.entity.Admin;
import com.bank.model.enums.AdminRole;
import com.bank.model.enums.AdminStatus;
import com.bank.model.repository.AdminRepository;
import com.bank.model.repository.UserRepository;
import com.bank.security.SessionManager;
import com.bank.service.AccountService;
import com.bank.service.AdminAuthService;
import com.bank.service.AuditLogService;
import com.bank.service.AuthService;
import com.bank.service.AuthenticationService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginScreenSuperAdminNavigationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private AuditLogService auditLogService;

    private AuthController authController;
    private ScreenNavigator navigator;
    private TUISession session;

    private Admin sampleSuperAdmin;

    @BeforeEach
    void setUp() {
        SessionManager.logout();
        navigator = new ScreenNavigator();
        session = TUISession.getInstance();
        session.logout();

        sampleSuperAdmin = Admin.builder()
                .adminId(1L)
                .username("superadmin")
                .email("superadmin@digibank.local")
                .passwordHash("$2a$12$85D9VpUlX/kGWjEfszWdeuECU8307jeMxS2mifHq/hExamkbtDeUm")
                .fullName("System Administrator")
                .role(AdminRole.SUPER_ADMIN)
                .status(AdminStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        AuthenticationService authService = new AuthenticationService(userRepository, adminRepository, accountService, auditLogService);
        AuthService legacyAuthService = new AuthService(userRepository, accountService, null, adminRepository);
        AdminAuthService adminAuthService = new AdminAuthService(adminRepository, auditLogService);
        authController = new AuthController(authService, legacyAuthService, adminAuthService);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        session.logout();
    }

    @Test
    @DisplayName("Verify superadmin login with password '1234' transitions session and routes to SuperAdminDashboardScreen")
    void testSuperadminLoginTransitionsToSuperAdminDashboard() {
        when(adminRepository.findByEmail("superadmin")).thenReturn(Optional.empty());
        when(adminRepository.findByUsername("superadmin")).thenReturn(Optional.of(sampleSuperAdmin));

        AuthenticatedUser authUser = authController.login("superadmin", "1234");
        assertNotNull(authUser);
        assertTrue(authUser.isAdmin());

        // Simulate LoginScreen session update and routing logic
        if (authUser.isCustomer()) {
            session.setCurrentUser(authUser.getUserDTO());
        } else {
            session.setCurrentAdmin(authUser.getAdminDTO());
            if (SessionManager.getCurrentAdmin() == null && authUser.getAdminDTO() != null) {
                Admin adminEntity = Admin.builder()
                        .adminId(authUser.getAdminDTO().getAdminId())
                        .username(authUser.getAdminDTO().getUsername())
                        .email(authUser.getAdminDTO().getEmail())
                        .fullName(authUser.getAdminDTO().getFullName())
                        .role(authUser.getAdminDTO().getRole())
                        .status(AdminStatus.ACTIVE)
                        .build();
                SessionManager.loginAdmin(adminEntity);
            }
        }
        session.setCurrentAuthenticatedUser(authUser);

        if (authUser.isCustomer()) {
            navigator.clearAndPush(new CustomerDashboardScreen());
        } else if (authUser.isAdmin() || (authUser.getAdminDTO() != null && authUser.getAdminDTO().getRole() == AdminRole.SUPER_ADMIN)) {
            navigator.clearAndPush(new SuperAdminDashboardScreen());
        } else {
            navigator.clearAndPush(new StaffDashboardScreen());
        }

        // Verify session state
        assertTrue(session.isAdminLoggedIn(), "TUISession should reflect logged-in admin");
        assertEquals("superadmin", session.getCurrentAdmin().getUsername());
        assertEquals(AdminRole.SUPER_ADMIN, session.getCurrentAdmin().getRole());
        assertTrue(SessionManager.isAdminLoggedIn(), "SessionManager should hold admin session");
        assertEquals(AdminRole.SUPER_ADMIN, SessionManager.getCurrentAdmin().getRole());

        // Verify screen transition
        assertTrue(navigator.getCurrentScreen() instanceof SuperAdminDashboardScreen,
                "Navigator top screen must be SuperAdminDashboardScreen");
    }
}
