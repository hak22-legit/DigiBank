package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.*;
import com.bank.controller.AuthController;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.AdminDTO;
import com.bank.model.dto.AuthenticatedUser;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.Admin;
import com.bank.model.entity.AuditLog;
import com.bank.model.entity.FraudAlert;
import com.bank.model.enums.*;
import com.bank.security.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ScreenNavigationAndRbacTest {

    private ScreenNavigator navigator;
    private TUISession session;

    private static class DummyScreen implements Screen {
        @Override
        public void render(ScreenNavigator nav, TUISession sess) {}
    }

    @BeforeEach
    void setUp() {
        navigator = new ScreenNavigator();
        session = mock(TUISession.class);
        SessionManager.logout();
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    @DisplayName("RBAC: Unauthenticated user is rejected by all restricted screens and popped cleanly")
    void testUnauthenticatedUserRejectedFromRestrictedScreens() {
        DummyScreen baseScreen = new DummyScreen();

        // SuperAdminDashboardScreen
        navigator.push(baseScreen);
        SuperAdminDashboardScreen superAdminScreen = new SuperAdminDashboardScreen();
        navigator.push(superAdminScreen);
        superAdminScreen.render(navigator, session);
        assertTrue(navigator.getCurrentScreen() instanceof WelcomeScreen,
                "Unauthenticated admin must be redirected to WelcomeScreen from SuperAdminDashboardScreen");

        // LoanUnderwritingScreen
        navigator.clearAndPush(baseScreen);
        LoanUnderwritingScreen loanScreen = new LoanUnderwritingScreen();
        navigator.push(loanScreen);
        loanScreen.render(navigator, session);
        assertEquals(baseScreen, navigator.getCurrentScreen(), "Unauthenticated admin must be popped from LoanUnderwritingScreen");

        // FraudInvestigationScreen
        navigator.clearAndPush(baseScreen);
        FraudInvestigationScreen fraudScreen = new FraudInvestigationScreen();
        navigator.push(fraudScreen);
        fraudScreen.render(navigator, session);
        assertEquals(baseScreen, navigator.getCurrentScreen(), "Unauthenticated admin must be popped from FraudInvestigationScreen");

        // ComplianceUserForensicsScreen
        navigator.clearAndPush(baseScreen);
        ComplianceUserForensicsScreen forensicsScreen = new ComplianceUserForensicsScreen(1L);
        navigator.push(forensicsScreen);
        forensicsScreen.render(navigator, session);
        assertEquals(baseScreen, navigator.getCurrentScreen(), "Unauthenticated admin must be popped from ComplianceUserForensicsScreen");

        // AuditLogScreen
        navigator.clearAndPush(baseScreen);
        AuditLogScreen auditScreen = new AuditLogScreen();
        navigator.push(auditScreen);
        auditScreen.render(navigator, session);
        assertEquals(baseScreen, navigator.getCurrentScreen(), "Unauthenticated admin must be popped from AuditLogScreen");

        // StaffManagementScreen
        navigator.clearAndPush(baseScreen);
        StaffManagementScreen staffMgmtScreen = new StaffManagementScreen();
        navigator.push(staffMgmtScreen);
        staffMgmtScreen.render(navigator, session);
        assertEquals(baseScreen, navigator.getCurrentScreen(), "Unauthenticated admin must be popped from StaffManagementScreen");

        // FxConfigScreen
        navigator.clearAndPush(baseScreen);
        FxConfigScreen fxScreen = new FxConfigScreen();
        navigator.push(fxScreen);
        fxScreen.render(navigator, session);
        assertEquals(baseScreen, navigator.getCurrentScreen(), "Unauthenticated admin must be popped from FxConfigScreen");
    }

    @Test
    @DisplayName("RBAC: Loan Officer is redirected from SuperAdmin dashboard to Staff dashboard")
    void testLoanOfficerRedirectedFromSuperAdminDashboard() {
        Admin loanOfficer = Admin.builder()
                .adminId(2L)
                .username("officer_bob")
                .role(AdminRole.LOAN_OFFICER)
                .status(AdminStatus.ACTIVE)
                .build();
        SessionManager.loginAdmin(loanOfficer);

        AdminDTO loanOfficerDto = AdminDTO.builder()
                .adminId(2L)
                .username("officer_bob")
                .role(AdminRole.LOAN_OFFICER)
                .build();
        when(session.getCurrentAdmin()).thenReturn(loanOfficerDto);

        DummyScreen baseScreen = new DummyScreen();
        navigator.push(baseScreen);
        SuperAdminDashboardScreen superAdminScreen = new SuperAdminDashboardScreen();
        navigator.push(superAdminScreen);

        superAdminScreen.render(navigator, session);

        assertTrue(navigator.getCurrentScreen() instanceof StaffDashboardScreen,
                "Non-superadmin must be automatically rerouted to StaffDashboardScreen");
    }

    @Test
    @DisplayName("RBAC: Loan Officer is prohibited from accessing FraudInvestigationScreen")
    void testLoanOfficerForbiddenOnFraudInvestigation() {
        Admin loanOfficer = Admin.builder()
                .adminId(2L)
                .username("officer_bob")
                .role(AdminRole.LOAN_OFFICER)
                .status(AdminStatus.ACTIVE)
                .build();
        SessionManager.loginAdmin(loanOfficer);

        DummyScreen baseScreen = new DummyScreen();
        navigator.push(baseScreen);
        FraudInvestigationScreen fraudScreen = new FraudInvestigationScreen();
        navigator.push(fraudScreen);

        fraudScreen.render(navigator, session);

        assertEquals(baseScreen, navigator.getCurrentScreen(),
                "Loan Officer must be cleanly popped from FraudInvestigationScreen");
    }

    @Test
    @DisplayName("RBAC: Compliance Officer is prohibited from accessing LoanUnderwritingScreen")
    void testComplianceOfficerForbiddenOnLoanUnderwriting() {
        Admin complianceOfficer = Admin.builder()
                .adminId(3L)
                .username("officer_alice")
                .role(AdminRole.COMPLIANCE_OFFICER)
                .status(AdminStatus.ACTIVE)
                .build();
        SessionManager.loginAdmin(complianceOfficer);

        DummyScreen baseScreen = new DummyScreen();
        navigator.push(baseScreen);
        LoanUnderwritingScreen underwritingScreen = new LoanUnderwritingScreen();
        navigator.push(underwritingScreen);

        underwritingScreen.render(navigator, session);

        assertEquals(baseScreen, navigator.getCurrentScreen(),
                "Compliance Officer must be cleanly popped from LoanUnderwritingScreen");
    }

    @Test
    @DisplayName("RBAC: Non-SuperAdmin staff is prohibited from StaffManagementScreen and FxConfigScreen")
    void testStaffForbiddenOnSuperAdminConfigurationScreens() {
        Admin loanOfficer = Admin.builder()
                .adminId(2L)
                .username("officer_bob")
                .role(AdminRole.LOAN_OFFICER)
                .status(AdminStatus.ACTIVE)
                .build();
        SessionManager.loginAdmin(loanOfficer);

        DummyScreen baseScreen = new DummyScreen();

        // StaffManagementScreen
        navigator.clearAndPush(baseScreen);
        StaffManagementScreen staffScreen = new StaffManagementScreen();
        navigator.push(staffScreen);
        staffScreen.render(navigator, session);
        assertEquals(baseScreen, navigator.getCurrentScreen(), "Loan officer cannot access StaffManagementScreen");

        // FxConfigScreen
        navigator.clearAndPush(baseScreen);
        FxConfigScreen fxScreen = new FxConfigScreen();
        navigator.push(fxScreen);
        fxScreen.render(navigator, session);
        assertEquals(baseScreen, navigator.getCurrentScreen(), "Loan officer cannot access FxConfigScreen");

        Admin complianceOfficer = Admin.builder()
                .adminId(3L)
                .username("officer_alice")
                .role(AdminRole.COMPLIANCE_OFFICER)
                .status(AdminStatus.ACTIVE)
                .build();
        SessionManager.loginAdmin(complianceOfficer);

        // StaffManagementScreen
        navigator.clearAndPush(baseScreen);
        navigator.push(staffScreen);
        staffScreen.render(navigator, session);
        assertEquals(baseScreen, navigator.getCurrentScreen(), "Compliance officer cannot access StaffManagementScreen");

        // FxConfigScreen
        navigator.clearAndPush(baseScreen);
        navigator.push(fxScreen);
        fxScreen.render(navigator, session);
        assertEquals(baseScreen, navigator.getCurrentScreen(), "Compliance officer cannot access FxConfigScreen");
    }

    @Test
    @DisplayName("RBAC: AuthenticatedUser role discrimination properly classifies subject types")
    void testAuthenticatedUserRoleDiscrimination() {
        UserDTO customerDto = UserDTO.builder()
                .userId(100L)
                .username("johndoe")
                .email("john@example.com")
                .fullName("John Doe")
                .status(UserStatus.ACTIVE)
                .build();
        AuthenticatedUser customerAuth = AuthenticatedUser.fromCustomer(customerDto);
        assertTrue(customerAuth.isCustomer());
        assertFalse(customerAuth.isStaff());
        assertFalse(customerAuth.isAdmin());

        AdminDTO loanOfficerDto = AdminDTO.builder()
                .adminId(10L)
                .username("loanofficer1")
                .email("loan@digibank.internal")
                .fullName("Loan Officer One")
                .role(AdminRole.LOAN_OFFICER)
                .build();
        AuthenticatedUser staffAuth = AuthenticatedUser.fromAdmin(loanOfficerDto);
        assertFalse(staffAuth.isCustomer());
        assertTrue(staffAuth.isStaff());
        assertFalse(staffAuth.isAdmin());

        AdminDTO superAdminDto = AdminDTO.builder()
                .adminId(1L)
                .username("superadmin")
                .email("admin@digibank.internal")
                .fullName("Root Admin")
                .role(AdminRole.SUPER_ADMIN)
                .build();
        AuthenticatedUser superAdminAuth = AuthenticatedUser.fromAdmin(superAdminDto);
        assertFalse(superAdminAuth.isCustomer());
        assertFalse(superAdminAuth.isStaff());
        assertTrue(superAdminAuth.isAdmin());
    }

    @Test
    @DisplayName("CustomerDashboardScreen: Option 0 triggers logout, clears session, and navigates to WelcomeScreen")
    void testCustomerDashboardSignOut() {
        CustomerDashboardScreen customerScreen = new CustomerDashboardScreen();
        navigator.push(customerScreen);

        // Test choice 0 (quick hotkey 0, Esc, or Enter on option [0])
        customerScreen.executeAction(0, navigator, session);
        verify(session, atLeastOnce()).logout();
        assertTrue(navigator.getCurrentScreen() instanceof WelcomeScreen,
                "CustomerDashboardScreen action 0 must navigate to WelcomeScreen on sign out");

        // Re-push and test choice 0 again
        navigator.clearAndPush(customerScreen);
        customerScreen.executeAction(0, navigator, session);
        verify(session, atLeast(2)).logout();
        assertTrue(navigator.getCurrentScreen() instanceof WelcomeScreen,
                "CustomerDashboardScreen action 0 must navigate to WelcomeScreen on sign out");
    }

    @Test
    @DisplayName("Universal State Guard Layout: Render content across screens conforms to 82 columns")
    void testLayoutConformanceUnderBoundaryStatusMessages() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        String longStatusMessage = "Status: Action completed or temporarily deferred. Press [Esc] to return.";

        // 1. SuperAdminDashboardScreen
        String superAdminContent = SuperAdminDashboardScreen.renderContent(
                AdminDTO.builder().adminId(1L).username("admin").role(AdminRole.SUPER_ADMIN).build(),
                12, 450, 2L, 5, 1000L, List.of(), 0, longStatusMessage, false, width
        );
        verifyLinesFit(superAdminContent, width, "SuperAdminDashboardScreen");

        // 2. StaffManagementScreen Directory
        List<Admin> staffList = List.of(
                Admin.builder().adminId(1L).username("superadmin").role(AdminRole.SUPER_ADMIN).status(AdminStatus.ACTIVE).build()
        );
        String staffMgmtContent = StaffManagementScreen.renderContent(staffList, 0, longStatusMessage, true, width);
        verifyLinesFit(staffMgmtContent, width, "StaffManagementScreen");

        // 3. StaffManagementScreen Provisioning Form
        String provContent = StaffManagementScreen.renderProvisioningContent(
                staffList, "newuser", true, "password123", "New Staff", "new@digibank.internal", "012345678",
                0, longStatusMessage, true, width
        );
        verifyLinesFit(provContent, width, "StaffManagementScreen Provisioning");

        // 4. FxConfigScreen
        String fxContent = FxConfigScreen.renderContent(
                Map.of("KHR", new BigDecimal("4085.00")),
                new BigDecimal("0.00"), new BigDecimal("1.50"), new BigDecimal("10000.00"), new BigDecimal("5000.00"),
                0, longStatusMessage, true, width
        );
        verifyLinesFit(fxContent, width, "FxConfigScreen");

        // 5. FraudInvestigationScreen
        FraudAlert alert = FraudAlert.builder()
                .alertId(1L).accountId(5L).transactionId(10L).riskLevel(RiskLevel.HIGH)
                .status(FraudStatus.UNDER_INVESTIGATION).description("Extremely long anomaly velocity text")
                .build();
        Account account = Account.builder().accountId(5L).accountNumber("DGB-000000005").status(AccountStatus.ACTIVE).build();
        String fraudContent = FraudInvestigationScreen.renderContent(alert, account, 0, 1, 0, longStatusMessage, true, width);
        verifyLinesFit(fraudContent, width, "FraudInvestigationScreen");

        // 6. AuditLogScreen
        AuditLog log = AuditLog.builder()
                .logId(99L).action("SECURITY_BREACH_DETECTED").actorName("SYSTEM").targetTable("ACCOUNT").targetId(5L)
                .ipAddress("127.0.0.1").createdAt(java.time.LocalDateTime.now())
                .build();
        String auditContent = AuditLogScreen.renderContent(List.of(log), 0, 1, 1, 1, null, longStatusMessage, true, width);
        verifyLinesFit(auditContent, width, "AuditLogScreen");
    }

    private void verifyLinesFit(String content, int expectedWidth, String screenName) {
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            int visible = TUIBox.visibleLength(line);
            assertTrue(visible <= expectedWidth,
                    String.format("%s line %d exceeds width limit (%d vs %d): %s", screenName, i, visible, expectedWidth, line));
            // Box lines with borders must be exact width
            if (i < lines.length - 1 && (line.contains("│") || line.contains("─"))) {
                assertEquals(expectedWidth, visible,
                        String.format("%s boxed line %d is not exactly width %d (got %d): %s", screenName, i, expectedWidth, visible, line));
            }
        }
    }
}
