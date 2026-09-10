package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.controller.LoanController;
import com.bank.database.DatabaseConnection;
import com.bank.model.PagedResult;
import com.bank.model.SystemStats;
import com.bank.model.dto.AdminDTO;
import com.bank.model.entity.Admin;
import com.bank.model.entity.AuditLog;
import com.bank.model.entity.FraudAlert;
import com.bank.model.entity.Loan;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * SCREEN 10: ADMIN DASHBOARD (STAFF PORTAL) (82 Columns)
 * Non-blocking keyboard navigation with zero trailing prompts.
 */
public class AdminDashboardScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardScreen.class);

    private final AdminController adminController;
    private final LoanController loanController;
    private String statusMessage;
    private boolean isErrorStatus;

    public AdminDashboardScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getLoanController());
    }

    public AdminDashboardScreen(AdminController adminController, LoanController loanController) {
        this.adminController = adminController;
        this.loanController = loanController;
        this.statusMessage = null;
        this.isErrorStatus = false;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        AdminDTO adminDto = session.getCurrentAdmin();
        Admin adminEntity = SessionManager.getCurrentAdmin();
        if (adminDto == null || adminEntity == null) {
            navigator.clearAndPush(new WelcomeScreen());
            return;
        }

        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;
        boolean firstRender = true;

        try {
            while (true) {
                StringBuilder sb = new StringBuilder();
                if (firstRender) {
                    sb.append(ConsoleTheme.CLEAR_SCREEN);
                } else {
                    sb.append("\u001B[H");
                }

                // Top Border
                sb.append(TUIBox.top(width)).append("\n");

                // Header
                String adminName = adminDto.getUsername() != null ? adminDto.getUsername() : "admin";
                String adminRole = adminDto.getRole() != null ? adminDto.getRole().name() : "SUPER_ADMIN";
                String sessionStr = adminDto.getAdminId() != null ? "#ADM-" + adminDto.getAdminId() : "#ADM-SESSION";
                String headerLine = String.format("DIGIBANK CORE │ ADMIN: %s │ ROLE: %s │ SESSION: %s", adminName, adminRole, sessionStr);
                sb.append(TUIBox.line(" " + ConsoleTheme.bold(headerLine), width)).append("\n");

                // Section 1: SYSTEM HEALTH & RADAR
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line(" " + ConsoleTheme.bold("SYSTEM HEALTH & RADAR"), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                int activeConn = DatabaseConnection.getActiveConnections();
                int totalConn = DatabaseConnection.getTotalConnections();
                if (totalConn <= 0) totalConn = 20;

                long openFraud = 0;
                long auditCount = 0;
                try {
                    SystemStats stats = adminController.getSystemStats(adminEntity);
                    if (stats != null) {
                        openFraud = stats.getOpenFraudAlerts();
                    }
                } catch (Exception ignored) {}

                int pendingLoansCount = 0;
                try {
                    List<Loan> pendingLoans = loanController.getPendingLoans(adminEntity);
                    if (pendingLoans != null) {
                        pendingLoansCount = pendingLoans.size();
                    }
                } catch (Exception ignored) {}

                try {
                    PagedResult<AuditLog> auditResult = adminController.getAuditLogs(adminEntity, 1, 1);
                    if (auditResult != null) {
                        auditCount = auditResult.getTotalItems();
                    }
                } catch (Exception ignored) {}

                String connVal = activeConn + " / " + totalConn;
                String fraudVal = String.valueOf(openFraud);
                String loanVal = pendingLoansCount + (pendingLoansCount == 1 ? " Loan" : " Loans");
                String auditVal = String.format("%,d", auditCount);

                String healthRow1 = String.format("  HikariCP Connections : %-18s Unresolved Fraud Alerts : %s", connVal, fraudVal);
                String healthRow2 = String.format("  Pending Loan Reviews : %-18s Audit Entries Logged    : %s", loanVal, auditVal);
                sb.append(TUIBox.line(healthRow1, width)).append("\n");
                sb.append(TUIBox.line(healthRow2, width)).append("\n");

                // Section 2: FRAUD ALERTS & THREATS
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line(" " + ConsoleTheme.bold("FRAUD ALERTS & THREATS (fraud_alerts table)"), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String thRow = String.format("  %-9s %-8s %-11s %-8s %-36s", "Alert ID", "User ID", "Risk Level", "Status", "Description");
                sb.append(TUIBox.line(thRow, width)).append("\n");
                String thDivider = "  ───────── ──────── ─────────── ──────── ────────────────────────────────────";
                sb.append(TUIBox.line(thDivider, width)).append("\n");

                List<FraudAlert> alerts = null;
                try {
                    alerts = adminController.getAllFraudAlerts(adminEntity);
                } catch (Exception ignored) {}

                if (alerts != null && !alerts.isEmpty()) {
                    int displayed = 0;
                    for (FraudAlert alert : alerts) {
                        if (displayed >= 5) break;
                        String aId = "#ALT-" + alert.getAlertId();
                        String uId = alert.getUserId() != null ? "#USR-" + alert.getUserId() : (alert.getAccountId() != null ? "#ACC-" + alert.getAccountId() : "-");
                        String rLvl = alert.getRiskLevel() != null ? alert.getRiskLevel().name() : "-";
                        String st = alert.getStatus() != null ? alert.getStatus().name() : "-";
                        String desc = alert.getDescription() != null ? alert.getDescription() : "-";
                        if (desc.length() > 36) {
                            desc = desc.substring(0, 33) + "...";
                        }
                        String alertRow = String.format("  %-9s %-8s %-11s %-8s %-36s", aId, uId, rLvl, st, desc);
                        sb.append(TUIBox.line(alertRow, width)).append("\n");
                        displayed++;
                    }
                } else {
                    sb.append(TUIBox.line("  " + ConsoleTheme.muted("No active fraud alerts detected across accounts."), width)).append("\n");
                }

                // Section 3: ADMINISTRATIVE ACTIONS
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line(" " + ConsoleTheme.bold("ADMINISTRATIVE ACTIONS"), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String opt1 = "[1] Underwrite Pending Loans (LOAN_OFFICER / SUPER_ADMIN)";
                String opt2 = "[2] Triage Fraud Alerts & Freeze Accounts (COMPLIANCE / SUPER_ADMIN)";
                String opt3 = "[3] Inspect System Audit Logs (audit_logs table)";
                String opt4 = "[4] Create / Manage Staff Credentials";
                String opt5 = "[5] Log Out";

                sb.append(TUIBox.line(selectedIndex == 0 ? ("   ► " + ConsoleTheme.highlight(opt1)) : ("     " + opt1), width)).append("\n");
                sb.append(TUIBox.line(selectedIndex == 1 ? ("   ► " + ConsoleTheme.highlight(opt2)) : ("     " + opt2), width)).append("\n");
                sb.append(TUIBox.line(selectedIndex == 2 ? ("   ► " + ConsoleTheme.highlight(opt3)) : ("     " + opt3), width)).append("\n");
                sb.append(TUIBox.line(selectedIndex == 3 ? ("   ► " + ConsoleTheme.highlight(opt4)) : ("     " + opt4), width)).append("\n");
                sb.append(TUIBox.line(selectedIndex == 4 ? ("   ► " + ConsoleTheme.highlight(opt5)) : ("     " + opt5), width)).append("\n");

                sb.append(TUIBox.bottom(width)).append("\n");

                if (statusMessage != null) {
                    String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                    sb.append(" Status: ").append(statusDisplay).append("\n");
                }
                sb.append(ConsoleTheme.muted("  [↑/↓] Navigate  •  [Enter] Select  •  [1-5] Quick Select  •  [Esc] Logout")).append("\n");

                System.out.print(sb.toString());
                System.out.flush();
                firstRender = false;

                int ch = reader.read();

                if (ch == 27) { // ESC or Escape sequence
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        terminal.setAttributes(origAttributes);
                        ControllerFactory.getAuthController().logoutAdmin();
                        session.logout();
                        navigator.clearAndPush(new WelcomeScreen());
                        return;
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A') { // Up
                            selectedIndex = (selectedIndex - 1 + 5) % 5;
                        } else if (code == 'B') { // Down
                            selectedIndex = (selectedIndex + 1) % 5;
                        }
                    }
                } else if (ch == '\t') {
                    selectedIndex = (selectedIndex + 1) % 5;
                } else if (ch == '\r' || ch == '\n') {
                    terminal.setAttributes(origAttributes);
                    if (selectedIndex == 0) {
                        navigator.push(new AdminLoanScreen());
                        return;
                    } else if (selectedIndex == 1) {
                        navigator.push(new AdminFraudScreen());
                        return;
                    } else if (selectedIndex == 2) {
                        navigator.push(new AdminAuditLogScreen());
                        return;
                    } else if (selectedIndex == 3) {
                        navigator.push(new AdminUserManagementScreen());
                        return;
                    } else if (selectedIndex == 4) {
                        boolean confirm = ConsolePrompt.promptConfirmation("Sign out of Administrative Console?");
                        if (confirm) {
                            ControllerFactory.getAuthController().logoutAdmin();
                            session.logout();
                            navigator.clearAndPush(new WelcomeScreen());
                            return;
                        } else {
                            origAttributes = terminal.enterRawMode();
                            firstRender = true;
                        }
                    }
                } else if (ch == '1') {
                    terminal.setAttributes(origAttributes);
                    navigator.push(new AdminLoanScreen());
                    return;
                } else if (ch == '2') {
                    terminal.setAttributes(origAttributes);
                    navigator.push(new AdminFraudScreen());
                    return;
                } else if (ch == '3') {
                    terminal.setAttributes(origAttributes);
                    navigator.push(new AdminAuditLogScreen());
                    return;
                } else if (ch == '4') {
                    terminal.setAttributes(origAttributes);
                    navigator.push(new AdminUserManagementScreen());
                    return;
                } else if (ch == '5') {
                    terminal.setAttributes(origAttributes);
                    boolean confirm = ConsolePrompt.promptConfirmation("Sign out of Administrative Console?");
                    if (confirm) {
                        ControllerFactory.getAuthController().logoutAdmin();
                        session.logout();
                        navigator.clearAndPush(new WelcomeScreen());
                        return;
                    } else {
                        origAttributes = terminal.enterRawMode();
                        firstRender = true;
                    }
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            logger.error("Error in admin dashboard loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }
}
