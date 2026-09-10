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

/**
 * Main dashboard and operational portal for authenticated bank staff (Loan Officers, Compliance Officers).
 */
public class StaffPortalScreen implements Screen {
    private final AdminController adminController;
    private final AuthController authController;

    public StaffPortalScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getAuthController());
    }

    public StaffPortalScreen(AdminController adminController, AuthController authController) {
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

        ConsoleMenu menu = new ConsoleMenu()
                .setHeaderSubtitle(session.getAuthenticatedName())
                .setScreenTitle("Staff Portal")
                .addItem("01", "Customer Management", "View and verify customer directory")
                .addItem("02", "Account Management", "Oversight of checking & savings accounts")
                .addItem("03", "Transaction Monitoring", "Monitor high-value and cross-border activity")
                .addItem("04", "Loan Management", "Review and approve/reject loan applications")
                .addItem("05", "Fraud Alerts", "Investigate compliance alerts and flag accounts")
                .addItem("06", "Customer Support", "Review customer inquiries and account support")
                .addItem("07", "Reports", "Generate financial compliance and risk reports")
                .addItem("08", "Logout", "Securely sign out of staff terminal");

        ConsoleMenu.MenuItem selected = menu.select();

        if (selected == null || "08".equals(selected.getCode())) {
            boolean confirm = ConsolePrompt.promptConfirmation("Are you sure you want to sign out of the Staff Portal?");
            if (confirm) {
                authController.logoutAdmin();
                session.logout();
                navigator.clearAndPush(new LoginScreen());
            }
            return;
        }

        switch (selected.getCode()) {
            case "01" -> navigator.push(new AdminUserManagementScreen());
            case "02" -> handleAccountManagement(session);
            case "03" -> handleTransactionMonitoring(session);
            case "04" -> handleLoanManagement(navigator, session);
            case "05" -> handleFraudAlerts(navigator, session);
            case "06" -> handleCustomerSupport(session);
            case "07" -> navigator.push(new AdminDashboardScreen());
        }
    }

    private void handleCustomerManagement(TUISession session) {
        session.clearScreen();
        TUILayout.printHeader(session.getAuthenticatedName());
        TUILayout.printScreenTitle("Customer Directory");
        System.out.println(TUIBox.line(ConsoleTheme.info("Staff Access: View customer profile details"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.center(ConsoleTheme.muted("Active customer overview available in Sub-Phase 9"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        ConsolePrompt.pause("Press ENTER to return to Staff Portal...");
    }

    private void handleAccountManagement(TUISession session) {
        session.clearScreen();
        TUILayout.printHeader(session.getAuthenticatedName());
        TUILayout.printScreenTitle("Account Oversight");
        System.out.println(TUIBox.line(ConsoleTheme.info("Staff Access: Review checking and savings accounts"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.center(ConsoleTheme.muted("Account oversight module available in Sub-Phase 9"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        ConsolePrompt.pause("Press ENTER to return to Staff Portal...");
    }

    private void handleTransactionMonitoring(TUISession session) {
        session.clearScreen();
        TUILayout.printHeader(session.getAuthenticatedName());
        TUILayout.printScreenTitle("Transaction Monitoring");
        System.out.println(TUIBox.line(ConsoleTheme.info("Staff Access: Continuous real-time transaction surveillance"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.center(ConsoleTheme.muted("Transaction surveillance module available in Sub-Phase 9"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        ConsolePrompt.pause("Press ENTER to return to Staff Portal...");
    }

    private void handleLoanManagement(ScreenNavigator navigator, TUISession session) {
        navigator.push(new AdminLoanScreen());
    }

    private void handleFraudAlerts(ScreenNavigator navigator, TUISession session) {
        navigator.push(new AdminFraudScreen());
    }

    private void handleCustomerSupport(TUISession session) {
        session.clearScreen();
        TUILayout.printHeader(session.getAuthenticatedName());
        TUILayout.printScreenTitle("Customer Support Desk");
        System.out.println(TUIBox.line(ConsoleTheme.info("Staff Access: Customer service assistance desk"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.center(ConsoleTheme.muted("Support desk module available in Sub-Phase 9"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        ConsolePrompt.pause("Press ENTER to return to Staff Portal...");
    }

    private void handleReports(TUISession session) {
        session.clearScreen();
        TUILayout.printHeader(session.getAuthenticatedName());
        TUILayout.printScreenTitle("Staff Operational Reports");
        System.out.println(TUIBox.line(ConsoleTheme.info("Staff Access: Operational reporting summary"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.center(ConsoleTheme.muted("Operational reports module available in Sub-Phase 9"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        ConsolePrompt.pause("Press ENTER to return to Staff Portal...");
    }
}
