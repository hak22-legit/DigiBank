package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleMenu;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.controller.AuthController;
import com.bank.model.dto.AdminDTO;
import com.bank.model.enums.AdminRole;

/**
 * Administrative Main Portal screen with strict Role-Based Access Control (RBAC).
 * Adapts menu options dynamically according to SUPER_ADMIN, LOAN_OFFICER, and COMPLIANCE_OFFICER roles.
 */
public class AdminMainMenuScreen implements Screen {
    private final AdminController adminController;
    private final AuthController authController;

    public AdminMainMenuScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getAuthController());
    }

    public AdminMainMenuScreen(AdminController adminController) {
        this(adminController, ControllerFactory.getAuthController());
    }

    public AdminMainMenuScreen(AdminController adminController, AuthController authController) {
        this.adminController = adminController;
        this.authController = authController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        AdminDTO admin = session.getCurrentAdmin();
        if (admin == null) {
            navigator.clearAndPush(new LoginScreen());
            return;
        }

        session.clearScreen();
        TUILayout.printHeader(session.getAuthenticatedName());
        TUILayout.printScreenTitle("Administrative Portal — " + admin.getRole());

        AdminRole role = admin.getRole();
        ConsoleMenu menu = new ConsoleMenu();

        if (role == AdminRole.LOAN_OFFICER) {
            menu.addItem("01", "Dashboard", "System-wide metrics and overview")
                .addItem("02", "Loan Decisions", "Review and approve/reject pending loans")
                .addItem("03", "Logout", "Sign out of loan officer session");
        } else if (role == AdminRole.COMPLIANCE_OFFICER) {
            menu.addItem("01", "Dashboard", "System-wide metrics and overview")
                .addItem("02", "Fraud & Security", "Investigate alerts and freeze/unfreeze accounts")
                .addItem("03", "Audit Logs", "Review immutable regulatory and compliance logs")
                .addItem("04", "Logout", "Sign out of compliance officer session");
        } else {
            // SUPER_ADMIN or default
            menu.addItem("01", "Dashboard", "System-wide metrics and operational summary")
                .addItem("02", "User Management", "View registered customer profiles")
                .addItem("03", "Loan Management", "Loan underwriting and approval queue")
                .addItem("04", "Fraud & Security", "Investigate alerts and freeze/unfreeze accounts")
                .addItem("05", "Audit Logs", "Review immutable system audit trail")
                .addItem("06", "Logout", "Securely sign out of administrative terminal");
        }

        ConsoleMenu.MenuItem selected = menu.select();

        // Handle Logout
        boolean isLogout = selected == null ||
                (role == AdminRole.LOAN_OFFICER && "03".equals(selected.getCode())) ||
                (role == AdminRole.COMPLIANCE_OFFICER && "04".equals(selected.getCode())) ||
                (role == AdminRole.SUPER_ADMIN && "06".equals(selected.getCode()));

        if (isLogout) {
            boolean confirm = ConsolePrompt.promptConfirmation("Are you sure you want to sign out of the Admin Portal?");
            if (confirm) {
                authController.logoutAdmin();
                session.logout();
                navigator.clearAndPush(new LoginScreen());
            }
            return;
        }

        if (role == AdminRole.LOAN_OFFICER) {
            switch (selected.getCode()) {
                case "01" -> navigator.push(new AdminDashboardScreen());
                case "02" -> navigator.push(new AdminLoanScreen());
            }
        } else if (role == AdminRole.COMPLIANCE_OFFICER) {
            switch (selected.getCode()) {
                case "01" -> navigator.push(new AdminDashboardScreen());
                case "02" -> navigator.push(new AdminFraudScreen());
                case "03" -> navigator.push(new AdminAuditLogScreen());
            }
        } else {
            // SUPER_ADMIN
            switch (selected.getCode()) {
                case "01" -> navigator.push(new AdminDashboardScreen());
                case "02" -> navigator.push(new AdminUserManagementScreen());
                case "03" -> navigator.push(new AdminLoanScreen());
                case "04" -> navigator.push(new AdminFraudScreen());
                case "05" -> navigator.push(new AdminAuditLogScreen());
            }
        }
    }
}