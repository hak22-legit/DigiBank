package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.ui.Ansi;
import com.bank.controller.AuthController;
import com.bank.controller.LoanController;
import com.bank.database.DatabaseConnection;
import com.bank.model.PagedResult;
import com.bank.model.SystemStats;
import com.bank.model.dto.AdminDTO;
import com.bank.model.entity.Admin;
import com.bank.model.entity.AuditLog;
import com.bank.model.entity.FraudAlert;
import com.bank.model.enums.AdminRole;
import com.bank.security.SessionManager;
import com.bank.service.LoanService.LoanPipelineStats;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;

/**
 * SCREEN: STAFF DASHBOARD & WORKFLOW (82 Columns)
 * Dynamic role-based operational radar and workflow for LOAN_OFFICER and COMPLIANCE_OFFICER.
 * Super Admin sessions automatically route to SuperAdminDashboardScreen.
 */
public class StaffDashboardScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(StaffDashboardScreen.class);

    private final AdminController adminController;
    private final LoanController loanController;
    private final AuthController authController;

    public StaffDashboardScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getLoanController(), ControllerFactory.getAuthController());
    }

    public StaffDashboardScreen(AdminController adminController, LoanController loanController) {
        this(adminController, loanController, ControllerFactory.getAuthController());
    }

    public StaffDashboardScreen(AdminController adminController, LoanController loanController, AuthController authController) {
        this.adminController = adminController;
        this.loanController = loanController;
        this.authController = authController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        AdminDTO currentAdmin = session.getCurrentAdmin();
        Admin adminEntity = SessionManager.getCurrentAdmin();
        if (currentAdmin == null || adminEntity == null) {
            navigator.clearAndPush(new WelcomeScreen());
            return;
        }

        if (currentAdmin.getRole() == AdminRole.LOAN_OFFICER) {
            renderLoanOfficerDashboard(navigator, session, currentAdmin, adminEntity);
        } else if (currentAdmin.getRole() == AdminRole.COMPLIANCE_OFFICER) {
            renderComplianceDashboard(navigator, session, currentAdmin, adminEntity);
        } else {
            renderSuperAdminDashboard(navigator, session);
        }
    }

    private void renderSuperAdminDashboard(ScreenNavigator navigator, TUISession session) {
        navigator.replace(new SuperAdminDashboardScreen(adminController, loanController));
    }

    // =========================================================================
    // LOAN OFFICER DASHBOARD
    // =========================================================================

    private void renderLoanOfficerDashboard(ScreenNavigator navigator, TUISession session, AdminDTO adminDto, Admin adminEntity) {
        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;
        boolean firstRender = true;
        boolean running = true;
        String statusMessage = "Ready. Underwriting queue synchronized.";

        try {
            while (running) {
                try {
                    LoanPipelineStats stats;
                    try {
                        stats = loanController.getUnderwritingPipelineStats(adminEntity);
                    } catch (Exception e) {
                        logger.warn("Could not fetch loan pipeline stats", e);
                        stats = new LoanPipelineStats(0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0);
                    }

                    if (stats != null && stats.applicationsInQueue() > 0) {
                        statusMessage = String.format("Ready. %d credit facilities require underwriting decision.", stats.applicationsInQueue());
                    } else if (statusMessage == null || statusMessage.startsWith("Ready.")) {
                        statusMessage = "Ready. Underwriting queue clear. All applications processed.";
                    }

                    String rendered = renderLoanOfficerContent(adminDto, stats, selectedIndex, statusMessage, width);
                    ScreenRenderer.render(rendered, firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && event.ch() == '0')) {
                        running = false;
                        authController.logoutAdmin();
                        session.logout();
                        navigator.clearAndPush(new WelcomeScreen());
                        return;
                    } else if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'k' || event.ch() == 'K'))) {
                        selectedIndex = (selectedIndex - 1 + 4) % 4;
                    } else if (event.action() == KeyAction.DOWN || (event.action() == KeyAction.CHAR && (event.ch() == 'j' || event.ch() == 'J'))) {
                        selectedIndex = (selectedIndex + 1) % 4;
                    } else if (event.action() == KeyAction.CHAR && event.ch() == '1') {
                        running = false;
                        navigator.push(new LoanUnderwritingScreen(adminController, loanController));
                        return;
                    } else if (event.action() == KeyAction.CHAR && event.ch() == '2') {
                        running = false;
                        navigator.push(new BorrowingHistoryScreen(adminController, loanController));
                        return;
                    } else if (event.action() == KeyAction.CHAR && event.ch() == '3') {
                        running = false;
                        navigator.push(new ActiveLoanBookScreen(adminController, loanController));
                        return;
                    } else if (event.action() == KeyAction.ENTER) {
                        running = false;
                        switch (selectedIndex) {
                            case 0 -> {
                                navigator.push(new LoanUnderwritingScreen(adminController, loanController));
                                return;
                            }
                            case 1 -> {
                                navigator.push(new BorrowingHistoryScreen(adminController, loanController));
                                return;
                            }
                            case 2 -> {
                                navigator.push(new ActiveLoanBookScreen(adminController, loanController));
                                return;
                            }
                            case 3 -> {
                                authController.logoutAdmin();
                                session.logout();
                                navigator.clearAndPush(new WelcomeScreen());
                                return;
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Loan Officer dashboard error recovery", ex);
                    statusMessage = "Status: Action completed or temporarily deferred. Press [Esc] to return.";
                }
            }
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void printMenuOption(int optionNum, String label, boolean isSelected) {
        String prefix = isSelected ? "▸ " : "  ";
        String plainText = String.format("%s[%d] %s", prefix, optionNum, label);

        // Pad strictly to 74 printable characters so the highlight spans from left to right border
        if (plainText.length() > 74) {
            plainText = plainText.substring(0, 74);
        } else {
            plainText = String.format("%-74s", plainText);
        }

        if (isSelected) {
            // Reverse video spans the exact 74 characters between pipes
            System.out.printf("│\033[7m%s\033[0m│%n", plainText);
        } else {
            System.out.printf("│%s│%n", plainText);
        }
    }

    public static String renderLoanOfficerContent(AdminDTO adminDto, LoanPipelineStats stats, int selectedIndex, String statusMessage, int width) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        sb.append(TUIBox.top(width)).append("\n");

        // Header
        String name = adminDto != null && adminDto.getUsername() != null ? adminDto.getUsername() : "loan1";
        String role = "LOAN_OFFICER";
        String sid = adminDto != null && adminDto.getAdminId() != null ? "#ADM-" + adminDto.getAdminId() : "#ADM-4";
        String headerLine = String.format("DIGIBANK CORE | OFFICER: %s | ROLE: %s | SESSION: %s", name, role, sid);
        sb.append(TUIBox.line(" " + ConsoleTheme.bold(headerLine), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 1: PENDING UNDERWRITING PIPELINE
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("PENDING UNDERWRITING PIPELINE"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        long qCount = stats != null ? stats.applicationsInQueue() : 0;
        BigDecimal qVol = stats != null && stats.totalVolumePending() != null ? stats.totalVolumePending() : BigDecimal.ZERO;
        long appCount = stats != null ? stats.approvedTodayCount() : 0;
        BigDecimal appVol = stats != null && stats.approvedTodayVolume() != null ? stats.approvedTodayVolume() : BigDecimal.ZERO;
        long rejCount = stats != null ? stats.rejectedTodayCount() : 0;

        String qStr = qCount + (qCount == 1 ? " Request" : " Requests");
        String appStr = appCount + " ($" + df.format(appVol) + ")";
        String volStr = "$ " + df.format(qVol) + " USD";
        String rejStr = rejCount + (rejCount == 1 ? " Application" : " Applications");

        sb.append(TUIBox.twoColumns(" Applications in Queue: " + qStr, "Approved Today : " + appStr + " ", width)).append("\n");
        sb.append(TUIBox.twoColumns(" Total Volume Pending : " + volStr, "Rejected Today : " + rejStr + " ", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 2: OFFICER ACTIONS
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("OFFICER ACTIONS"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String[] actionLabels = {
                String.format("Review Loan Underwriting Queue (%d Pending)", qCount),
                "Search Customer Borrowing History & Profiles",
                "Portfolio Performance & Active Loan Book"
        };

        for (int i = 0; i < 3; i++) {
            boolean isSel = (selectedIndex == i);
            String prefix = isSel ? "▸ " : "  ";
            String plainText = String.format("%s[%d] %s", prefix, i + 1, actionLabels[i]);

            // Pad strictly to 74 printable characters so the highlight spans from left to right border
            if (plainText.length() > 74) {
                plainText = plainText.substring(0, 74);
            } else {
                plainText = String.format("%-74s", plainText);
            }

            if (isSel) {
                sb.append(TUIBox.line("  \033[7m" + plainText + "\033[0m", width)).append("\n");
            } else {
                String rendered = plainText.replace("[" + (i + 1) + "]", Ansi.yellow("[" + (i + 1) + "]"));
                sb.append(TUIBox.line("  " + rendered, width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Centered [0] Sign Out & Terminate Session
        boolean selSignOut = (selectedIndex == 3);
        String prefix0 = selSignOut ? "▸ " : "  ";
        String signOutText = String.format("%s[0] Sign Out & Terminate Session", prefix0);
        String centeredPlain = " ".repeat(19) + signOutText;
        if (centeredPlain.length() > 74) {
            centeredPlain = centeredPlain.substring(0, 74);
        } else {
            centeredPlain = String.format("%-74s", centeredPlain);
        }

        if (selSignOut) {
            sb.append(TUIBox.line("  \033[7m" + centeredPlain + "\033[0m", width)).append("\n");
        } else {
            String rendered = centeredPlain.replace("[0]", Ansi.yellow("[0]"));
            sb.append(TUIBox.line("  " + rendered, width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        if (statusMessage != null && statusMessage.startsWith("Status: ")) {
            statusMessage = statusMessage.substring(8);
        }
        if (statusMessage != null && statusMessage.length() > 68) {
            statusMessage = statusMessage.substring(0, 65) + "...";
        }
        String statusDisplay = statusMessage != null && statusMessage.contains("Ready.")
                ? statusMessage.replace("Ready.", Ansi.green("Ready."))
                : ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        sb.append(Ansi.keyGuide("[↑/↓/←/→] Navigate  •  [1-4, 0] Quick Hotkey  •  [Enter] Select  •  [Esc] Logout")).append("\n");

        return sb.toString();
    }

    // =========================================================================
    // COMPLIANCE OFFICER DASHBOARD
    // =========================================================================

    private void renderComplianceDashboard(ScreenNavigator navigator, TUISession session, AdminDTO adminDto, Admin adminEntity) {
        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;
        boolean firstRender = true;
        boolean running = true;
        String statusMessage = "Surveillance active. Scanning transaction feeds.";

        try {
            while (running) {
                try {
                    int activeConn = DatabaseConnection.getActiveConnections();
                    int totalConn = DatabaseConnection.getTotalConnections();
                    if (totalConn <= 0) totalConn = 2;

                    long activeAlerts = 0;
                    long unresolvedThreats = 0;
                    long auditTrailCount = 0;
                    List<FraudAlert> alerts = Collections.emptyList();

                    try {
                        SystemStats stats = adminController.getSystemStats(adminEntity);
                        if (stats != null) {
                            activeAlerts = stats.getOpenFraudAlerts();
                        }
                    } catch (Exception ignored) {}

                    try {
                        alerts = adminController.getAllFraudAlerts(adminEntity);
                        if (alerts == null) alerts = Collections.emptyList();
                        unresolvedThreats = alerts.stream().filter(a -> "OPEN".equalsIgnoreCase(a.getStatus().name())).count();
                    } catch (Exception ignored) {}

                    try {
                        PagedResult<AuditLog> auditResult = adminController.getAuditLogs(adminEntity, 1, 1);
                        if (auditResult != null) {
                            auditTrailCount = auditResult.getTotalItems();
                        }
                    } catch (Exception ignored) {}

                    if (statusMessage == null || statusMessage.startsWith("Surveillance active")) {
                        statusMessage = String.format("Surveillance active. %d confirmed threat record logged.", alerts.size());
                    }

                    String rendered = renderComplianceOfficerContent(adminDto, activeConn, totalConn, activeAlerts,
                            unresolvedThreats, auditTrailCount, alerts, selectedIndex, statusMessage, width);
                    ScreenRenderer.render(rendered, firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && event.ch() == '0')) {
                        running = false;
                        authController.logoutAdmin();
                        session.logout();
                        navigator.clearAndPush(new WelcomeScreen());
                        return;
                    } else if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'k' || event.ch() == 'K'))) {
                        selectedIndex = (selectedIndex - 1 + 3) % 3;
                    } else if (event.action() == KeyAction.DOWN || (event.action() == KeyAction.CHAR && (event.ch() == 'j' || event.ch() == 'J'))) {
                        selectedIndex = (selectedIndex + 1) % 3;
                    } else if (event.action() == KeyAction.CHAR && event.ch() == '1') {
                        running = false;
                        FraudAlert target = !alerts.isEmpty() ? alerts.get(0) : null;
                        navigator.push(new FraudInvestigationScreen(adminController, target));
                        return;
                    } else if (event.action() == KeyAction.CHAR && event.ch() == '2') {
                        running = false;
                        navigator.push(new AuditLogScreen(adminController));
                        return;
                    } else if (event.action() == KeyAction.ENTER) {
                        running = false;
                        switch (selectedIndex) {
                            case 0 -> {
                                FraudAlert target = !alerts.isEmpty() ? alerts.get(0) : null;
                                navigator.push(new FraudInvestigationScreen(adminController, target));
                                return;
                            }
                            case 1 -> {
                                navigator.push(new AuditLogScreen(adminController));
                                return;
                            }
                            case 2 -> {
                                authController.logoutAdmin();
                                session.logout();
                                navigator.clearAndPush(new WelcomeScreen());
                                return;
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Compliance dashboard error recovery", ex);
                    statusMessage = "Status: Action completed or temporarily deferred. Press [Esc] to return.";
                }
            }
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    public static String renderComplianceOfficerContent(AdminDTO adminDto, int activeConn, int totalConn,
                                                        long activeAlerts, long unresolvedThreats,
                                                        long auditTrailCount, List<FraudAlert> alerts,
                                                        int selectedIndex, String statusMessage, int width) {
        StringBuilder sb = new StringBuilder();

        sb.append(TUIBox.top(width)).append("\n");

        // Header
        String name = adminDto != null && adminDto.getUsername() != null ? adminDto.getUsername() : "compliance1";
        String role = "COMPLIANCE_OFFICER";
        String sid = adminDto != null && adminDto.getAdminId() != null ? "#ADM-" + adminDto.getAdminId() : "#ADM-6";
        String headerLine = String.format("DIGIBANK CORE | OFFICER: %s | ROLE: %s | SESSION: %s", name, role, sid);
        sb.append(TUIBox.line(" " + ConsoleTheme.bold(headerLine), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 1: SYSTEM HEALTH & RADAR
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("SYSTEM HEALTH & RADAR"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String connVal = activeConn + " / " + totalConn;
        String alertVal = String.valueOf(activeAlerts);
        String threatVal = unresolvedThreats + " Threats";
        String auditVal = String.valueOf(auditTrailCount);

        sb.append(TUIBox.twoColumns(" HikariCP Connections : " + connVal, "Active Fraud Alerts : " + alertVal + " ", width)).append("\n");
        sb.append(TUIBox.twoColumns(" Unresolved Incidents : " + threatVal, "Audit Trail Entries : " + auditVal + " ", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 2: FRAUD ALERTS & AML SURVEILLANCE RADAR
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("FRAUD ALERTS & AML SURVEILLANCE RADAR"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Table Header: strictly 74 chars inside border
        String thContent = String.format("%-10s%-10s%-8s%-17s%-29s",
                "ALERT ID", "USER ID", "RISK", "STATUS", "INCIDENT DESCRIPTION");
        sb.append(TUIBox.line("  " + thContent, width)).append("\n");
        sb.append(TUIBox.line("  " + "─".repeat(74), width)).append("\n");

        if (alerts != null && !alerts.isEmpty()) {
            for (int i = 0; i < Math.min(3, alerts.size()); i++) {
                FraudAlert alert = alerts.get(i);
                String desc = alert.getDescription() != null ? alert.getDescription() : "-";
                if (desc.length() > 29) {
                    desc = desc.substring(0, 26) + "...";
                }
                String aid = "#ALT-" + (alert.getAlertId() != null ? String.format("%02d", alert.getAlertId()) : "01");
                String uid = alert.getUserId() != null ? String.format("#USR-%02d", alert.getUserId())
                        : (alert.getAccountId() != null ? String.format("#ACC-%02d", alert.getAccountId()) : "-");
                String risk = alert.getRiskLevel() != null ? alert.getRiskLevel().name() : "-";
                String st = alert.getStatus() != null ? alert.getStatus().name() : "-";

                String rowLine = String.format("%-10s%-10s%-8s%-17s%-29s", aid, uid, risk, st, desc);
                sb.append(TUIBox.line("  " + rowLine, width)).append("\n");
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No active fraud alerts detected across accounts."), width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 3: COMPLIANCE OFFICER ACTIONS
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("COMPLIANCE OFFICER ACTIONS"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String[] compActions = {
                "[1] Triage Fraud Alerts & Manage Account Holds",
                "[2] Inspect System Audit Logs & Forensic History"
        };

        for (int i = 0; i < 2; i++) {
            boolean isSel = (selectedIndex == i);
            String prefix = isSel ? "▸ " : "  ";
            String fullText = String.format("%s%s", prefix, compActions[i]);
            String plainLine = String.format("  %-70s", fullText);
            if (plainLine.length() > 74) plainLine = plainLine.substring(0, 74);
            String renderedLine = isSel ? ("\033[7m" + plainLine + "\033[0m") : plainLine;
            sb.append(TUIBox.line(renderedLine, width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Centered [0] Sign Out & Terminate Session
        boolean selSignOut = (selectedIndex == 2);
        String prefix0 = selSignOut ? "▸ " : "  ";
        String signOutText = prefix0 + "[0] Sign Out & Terminate Session";
        String centeredPlain = " ".repeat(19) + signOutText + " ".repeat(19);
        if (centeredPlain.length() > 74) centeredPlain = centeredPlain.substring(0, 74);
        String centeredRendered = selSignOut ? ("\033[7m" + centeredPlain + "\033[0m") : centeredPlain;
        sb.append(TUIBox.line(centeredRendered, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        if (statusMessage != null && statusMessage.startsWith("Status: ")) {
            statusMessage = statusMessage.substring(8);
        }
        if (statusMessage != null && statusMessage.length() > 68) {
            statusMessage = statusMessage.substring(0, 65) + "...";
        }
        String statusDisplay = ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Navigate  •  [Enter] Select  •  [1-2, 0] Hotkey  •  [Esc] Logout")).append("\n");

        return sb.toString();
    }
}
