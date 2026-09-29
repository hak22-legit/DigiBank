package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.TerminalInputHandler;
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
import com.bank.model.enums.RiskLevel;
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

        // Drain any lingering newline characters on entry
        TerminalInputHandler.drainBuffer(reader);

        int selectedIndex = 0;
        boolean firstRender = true;
        boolean inScreen = true;
        String statusMessage = "Ready. Underwriting queue synchronized.";

        try {
            while (inScreen) {
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

                    TerminalInputHandler.KeyCode event = TerminalInputHandler.readNavigationKey(reader);
                    String key = event.asNormalizedKey();

                    switch (key) {
                        case "ESC", "B", "0" -> {
                            inScreen = false;
                            authController.logoutAdmin();
                            session.logout();
                            navigator.clearAndPush(new WelcomeScreen());
                            return;
                        }
                        case "UP", "K" -> {
                            selectedIndex = (selectedIndex - 1 + 4) % 4;
                        }
                        case "DOWN", "J" -> {
                            selectedIndex = (selectedIndex + 1) % 4;
                        }
                        case "1" -> {
                            inScreen = false;
                            navigator.push(new LoanUnderwritingScreen(adminController, loanController));
                            return;
                        }
                        case "2" -> {
                            inScreen = false;
                            navigator.push(new BorrowingHistoryScreen(adminController, loanController));
                            return;
                        }
                        case "3" -> {
                            inScreen = false;
                            navigator.push(new ActiveLoanBookScreen(adminController, loanController));
                            return;
                        }
                        case "ENTER" -> {
                            switch (selectedIndex) {
                                case 0 -> {
                                    inScreen = false;
                                    navigator.push(new LoanUnderwritingScreen(adminController, loanController));
                                    return;
                                }
                                case 1 -> {
                                    inScreen = false;
                                    navigator.push(new BorrowingHistoryScreen(adminController, loanController));
                                    return;
                                }
                                case 2 -> {
                                    inScreen = false;
                                    navigator.push(new ActiveLoanBookScreen(adminController, loanController));
                                    return;
                                }
                                case 3 -> {
                                    inScreen = false;
                                    authController.logoutAdmin();
                                    session.logout();
                                    navigator.clearAndPush(new WelcomeScreen());
                                    return;
                                }
                            }
                        }
                        default -> {
                            // Discard unmapped keys without exiting or breaking the loop
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

        // Card 1: QUEUE STATUS (22w)
        String c1Top = ConsoleTheme.border("┌─── ") + ConsoleTheme.bold("QUEUE STATUS") + ConsoleTheme.border(" ───┐");
        String qCountStr = qCount > 0 ? (qCount + (qCount == 1 ? " Request" : " Requests")) : "00 Requests";
        String c1R1 = ConsoleTheme.border("│ ") + (qCount > 0 ? Ansi.yellow(String.format("%-18s", qCountStr)) : Ansi.green(String.format("%-18s", qCountStr))) + ConsoleTheme.border(" │");
        String c1R2 = ConsoleTheme.border("│ ") + (qCount > 0 ? Ansi.yellow(String.format("%-18s", "In Review")) : Ansi.green(String.format("%-18s", "Queue Clear [✓]"))) + ConsoleTheme.border(" │");
        String c1Bot = ConsoleTheme.border("└────────────────────┘");

        // Card 2: EXPOSURE AT RISK (24w)
        String c2Top = ConsoleTheme.border("┌── ") + ConsoleTheme.bold("EXPOSURE AT RISK") + ConsoleTheme.border(" ──┐");
        String volStr = "$ " + df.format(qVol);
        if (volStr.length() > 12) volStr = volStr.substring(0, 12);
        String c2R1 = ConsoleTheme.border("│ ") + ConsoleTheme.primary(String.format("%-12s", volStr)) + " " + ConsoleTheme.muted(String.format("%-7s", "Pending")) + ConsoleTheme.border(" │");
        String c2R2 = ConsoleTheme.border("│ ") + ConsoleTheme.muted(String.format("%-20s", "Total Volume")) + ConsoleTheme.border(" │");
        String c2Bot = ConsoleTheme.border("└──────────────────────┘");

        // Card 3: TODAY'S METRICS (28w)
        String c3Top = ConsoleTheme.border("┌───── ") + ConsoleTheme.bold("TODAY'S METRICS") + ConsoleTheme.border(" ────┐");
        String appText = "Approved: " + appCount + " ($" + df.format(appVol) + ")";
        if (appText.length() > 24) appText = appText.substring(0, 24);
        String c3R1 = ConsoleTheme.border("│ ") + Ansi.green(String.format("%-24s", appText)) + ConsoleTheme.border(" │");
        String rejText = "Rejected: " + rejCount + (rejCount == 1 ? " Application" : " Applications");
        if (rejText.length() > 24) rejText = rejText.substring(0, 24);
        String c3R2 = ConsoleTheme.border("│ ") + (rejCount > 0 ? Ansi.red(String.format("%-24s", rejText)) : ConsoleTheme.muted(String.format("%-24s", rejText))) + ConsoleTheme.border(" │");
        String c3Bot = ConsoleTheme.border("└──────────────────────────┘");

        sb.append(TUIBox.line(c1Top + "  " + c2Top + "  " + c3Top, width)).append("\n");
        sb.append(TUIBox.line(c1R1 + "  " + c2R1 + "  " + c3R1, width)).append("\n");
        sb.append(TUIBox.line(c1R2 + "  " + c2R2 + "  " + c3R2, width)).append("\n");
        sb.append(TUIBox.line(c1Bot + "  " + c2Bot + "  " + c3Bot, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 2: OFFICER ACTIONS
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("OFFICER ACTIONS"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String[] actionLabels = {
                String.format("Review Loan Underwriting Queue (%d)", qCount),
                "Search Customer Borrowing History",
                "Portfolio Performance & Active Loan Book"
        };

        for (int i = 0; i < 3; i++) {
            boolean isSel = (selectedIndex == i);
            String prefix = isSel ? "▸ " : "  ";
            String plainRow = String.format("  %s[%d] %-70s", prefix, i + 1, actionLabels[i]);
            if (isSel) {
                sb.append(TUIBox.fullWidthInverted(plainRow, width)).append("\n");
            } else {
                String rendered = plainRow.replace("[" + (i + 1) + "]", Ansi.yellow("[" + (i + 1) + "]"));
                sb.append(TUIBox.line(rendered, width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Option [0] left-aligned flush with main tasks
        boolean selSignOut = (selectedIndex == 3);
        String prefix0 = selSignOut ? "▸ " : "  ";
        String plain0 = String.format("  %s[0] %-70s", prefix0, "Sign Out & Terminate Session");
        if (selSignOut) {
            sb.append(TUIBox.fullWidthInverted(plain0, width)).append("\n");
        } else {
            String rendered0 = plain0.replace("[0]", Ansi.yellow("[0]"));
            sb.append(TUIBox.line(rendered0, width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        if (statusMessage != null && statusMessage.startsWith("Status: ")) {
            statusMessage = statusMessage.substring(8);
        }
        if (statusMessage != null && statusMessage.length() > 70) {
            statusMessage = statusMessage.substring(0, 67) + "...";
        }
        String statusDisplay = statusMessage != null && statusMessage.contains("Ready.")
                ? statusMessage.replace("Ready.", Ansi.green("Ready."))
                : ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Navigate • [1-3, 0] Direct Key • [Enter] Launch • [Esc] Logout")).append("\n");

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

        // Drain any lingering newline characters on entry
        TerminalInputHandler.drainBuffer(reader);

        int selectedIndex = 0;
        boolean firstRender = true;
        boolean inScreen = true;
        String statusMessage = "Surveillance active. Scanning transaction feeds.";

        try {
            while (inScreen) {
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

                    TerminalInputHandler.KeyCode event = TerminalInputHandler.readNavigationKey(reader);
                    String key = event.asNormalizedKey();

                    switch (key) {
                        case "ESC", "B", "0" -> {
                            inScreen = false;
                            authController.logoutAdmin();
                            session.logout();
                            navigator.clearAndPush(new WelcomeScreen());
                            return;
                        }
                        case "UP", "K" -> {
                            selectedIndex = (selectedIndex - 1 + 3) % 3;
                        }
                        case "DOWN", "J" -> {
                            selectedIndex = (selectedIndex + 1) % 3;
                        }
                        case "F", "1" -> {
                            inScreen = false;
                            FraudAlert target = !alerts.isEmpty() ? alerts.get(0) : null;
                            navigator.push(new FraudTriageScreen(adminController, target));
                            return;
                        }
                        case "2" -> {
                            inScreen = false;
                            navigator.push(new AuditLogScreen(adminController));
                            return;
                        }
                        case "ENTER" -> {
                            switch (selectedIndex) {
                                case 0 -> {
                                    inScreen = false;
                                    FraudAlert target = !alerts.isEmpty() ? alerts.get(0) : null;
                                    navigator.push(new FraudTriageScreen(adminController, target));
                                    return;
                                }
                                case 1 -> {
                                    inScreen = false;
                                    navigator.push(new AuditLogScreen(adminController));
                                    return;
                                }
                                case 2 -> {
                                    inScreen = false;
                                    authController.logoutAdmin();
                                    session.logout();
                                    navigator.clearAndPush(new WelcomeScreen());
                                    return;
                                }
                            }
                        }
                        default -> {
                            // Discard unmapped keys without exiting or breaking the loop
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

        // Section 1: SYSTEM HEALTH & RADAR (3-tier ASCII Cards)
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("SYSTEM HEALTH & RADAR"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Card 1: ACTIVE THREATS (22w)
        String c1Top = ConsoleTheme.border("┌─── ") + ConsoleTheme.bold("ACTIVE THREATS") + ConsoleTheme.border(" ─┐");
        String alertStr = (activeAlerts < 10 ? "0" + activeAlerts : String.valueOf(activeAlerts)) + " Open Alerts";
        String c1R1 = ConsoleTheme.border("│ ") + (activeAlerts > 0 ? Ansi.red(String.format("%-18s", alertStr)) : Ansi.green(String.format("%-18s", "00 Clear [✓]"))) + ConsoleTheme.border(" │");
        String c1R2 = ConsoleTheme.border("│ ") + ConsoleTheme.muted(String.format("%-18s", "System Perimeter")) + ConsoleTheme.border(" │");
        String c1Bot = ConsoleTheme.border("└────────────────────┘");

        // Card 2: AML INCIDENTS (24w)
        String c2Top = ConsoleTheme.border("┌──── ") + ConsoleTheme.bold("AML INCIDENTS") + ConsoleTheme.border(" ───┐");
        String amlStr = (unresolvedThreats < 10 ? "0" + unresolvedThreats : String.valueOf(unresolvedThreats)) + " Unresolved";
        String c2R1 = ConsoleTheme.border("│ ") + (unresolvedThreats > 0 ? Ansi.yellow(String.format("%-20s", amlStr)) : Ansi.green(String.format("%-20s", "00 Clean Feed [✓]"))) + ConsoleTheme.border(" │");
        String c2R2 = ConsoleTheme.border("│ ") + ConsoleTheme.muted(String.format("%-20s", "Surveillance Active")) + ConsoleTheme.border(" │");
        String c2Bot = ConsoleTheme.border("└──────────────────────┘");

        // Card 3: AUDIT TRAIL (28w)
        String c3Top = ConsoleTheme.border("┌──────── ") + ConsoleTheme.bold("AUDIT TRAIL") + ConsoleTheme.border(" ─────┐");
        String c3R1 = ConsoleTheme.border("│ ") + ConsoleTheme.primary(String.format("%-14s", auditTrailCount + " Entries")) + " " + Ansi.green("[✓]") + "      " + ConsoleTheme.border(" │");
        String c3R2 = ConsoleTheme.border("│ ") + ConsoleTheme.muted(String.format("%-24s", "Immutable Hash Chain")) + ConsoleTheme.border(" │");
        String c3Bot = ConsoleTheme.border("└──────────────────────────┘");

        sb.append(TUIBox.line(c1Top + "  " + c2Top + "  " + c3Top, width)).append("\n");
        sb.append(TUIBox.line(c1R1 + "  " + c2R1 + "  " + c3R1, width)).append("\n");
        sb.append(TUIBox.line(c1R2 + "  " + c2R2 + "  " + c3R2, width)).append("\n");
        sb.append(TUIBox.line(c1Bot + "  " + c2Bot + "  " + c3Bot, width)).append("\n");

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

        List<FraudAlert> sortedAlerts = new java.util.ArrayList<>(alerts != null ? alerts : Collections.emptyList());
        sortedAlerts.sort((a, b) -> {
            int rA = a.getRiskLevel() == RiskLevel.HIGH ? 3 : (a.getRiskLevel() == RiskLevel.MEDIUM ? 2 : 1);
            int rB = b.getRiskLevel() == RiskLevel.HIGH ? 3 : (b.getRiskLevel() == RiskLevel.MEDIUM ? 2 : 1);
            return Integer.compare(rB, rA);
        });

        if (!sortedAlerts.isEmpty()) {
            for (int i = 0; i < Math.min(3, sortedAlerts.size()); i++) {
                FraudAlert alert = sortedAlerts.get(i);
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
            if (sortedAlerts.size() > 3) {
                sb.append(TUIBox.line("  » ... and " + (sortedAlerts.size() - 3) + " more unresolved alerts. Press [1] to triage all incidents.", width)).append("\n");
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
            String plainLine = String.format("  %s%-74s", prefix, compActions[i]);
            if (isSel) {
                sb.append(TUIBox.fullWidthInverted(plainLine, width)).append("\n");
            } else {
                String rendered = plainLine.replace("[" + (i + 1) + "]", Ansi.yellow("[" + (i + 1) + "]"));
                sb.append(TUIBox.line(rendered, width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Flush [0] Sign Out & Terminate Session
        boolean selSignOut = (selectedIndex == 2);
        String prefix0 = selSignOut ? "▸ " : "  ";
        String plain0 = String.format("  %s[0] %-70s", prefix0, "Sign Out & Terminate Session");
        if (selSignOut) {
            sb.append(TUIBox.fullWidthInverted(plain0, width)).append("\n");
        } else {
            String rendered0 = plain0.replace("[0]", Ansi.yellow("[0]"));
            sb.append(TUIBox.line(rendered0, width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        if (statusMessage != null && statusMessage.startsWith("Status: ")) {
            statusMessage = statusMessage.substring(8);
        }
        if (statusMessage != null && statusMessage.length() > 70) {
            statusMessage = statusMessage.substring(0, 67) + "...";
        }
        String statusDisplay = ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Navigate • [1-2, 0] Hotkey • [F] Alert • [Enter] Select • [Esc] Logout")).append("\n");

        return sb.toString();
    }
}
