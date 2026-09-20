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
import com.bank.model.entity.Account;
import com.bank.model.entity.Admin;
import com.bank.model.entity.FraudAlert;
import com.bank.model.enums.FraudStatus;
import com.bank.model.enums.RiskLevel;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FRAUD DETECTION & THREAT TRIAGE (82 Columns)
 * Dynamic context-aware incident dossier screen with full-row highlights.
 */
public class FraudInvestigationScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(FraudInvestigationScreen.class);

    public enum ActionType {
        FREEZE,
        UNFREEZE,
        INVESTIGATE,
        CONFIRM_FRAUD,
        DISMISS_FALSE_POSITIVE,
        NEXT_INCIDENT,
        BACK_DASHBOARD,
        VIEW_USER_FORENSICS
    }

    public static class TriageActionItem {
        private final ActionType type;
        private final String label;

        public TriageActionItem(ActionType type, String label) {
            this.type = type;
            this.label = label;
        }

        public ActionType getType() { return type; }
        public String getLabel() { return label; }
    }

    private final AdminController adminController;
    private FraudAlert currentAlert;
    private String statusMessage = "Ready. Press [Enter] or [1-4] to execute action immediately.";
    private boolean isError = false;

    public FraudInvestigationScreen() {
        this(ControllerFactory.getAdminController(), null);
    }

    public FraudInvestigationScreen(FraudAlert alert) {
        this(ControllerFactory.getAdminController(), alert);
    }

    public FraudInvestigationScreen(AdminController adminController, FraudAlert alert) {
        this.adminController = adminController;
        this.currentAlert = alert;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        Admin admin = SessionManager.getCurrentAdmin();
        if (admin == null) {
            navigator.pop();
            return;
        }

        if (admin.getRole() != com.bank.model.enums.AdminRole.COMPLIANCE_OFFICER && admin.getRole() != com.bank.model.enums.AdminRole.SUPER_ADMIN) {
            logger.warn("Unauthorized role {} attempted to access FraudInvestigationScreen", admin.getRole());
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;
        boolean firstRender = true;
        boolean running = true;

        List<FraudAlert> allAlerts = Collections.emptyList();
        try {
            allAlerts = adminController.getAllFraudAlerts(admin);
            if (allAlerts == null) allAlerts = Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to load fraud alerts", e);
        }

        int alertIdx = 0;
        if (currentAlert != null) {
            for (int i = 0; i < allAlerts.size(); i++) {
                if (allAlerts.get(i).getAlertId().equals(currentAlert.getAlertId())) {
                    alertIdx = i;
                    break;
                }
            }
        } else if (!allAlerts.isEmpty()) {
            currentAlert = allAlerts.get(0);
        }

        try {
            while (running) {
                try {
                    boolean isEmptyState = (currentAlert == null || allAlerts.isEmpty());
                    Account account = null;

                    if (!isEmptyState) {
                        if (alertIdx >= allAlerts.size()) {
                            alertIdx = Math.max(0, allAlerts.size() - 1);
                        }
                        currentAlert = allAlerts.get(alertIdx);
                        if (currentAlert != null && currentAlert.getAccountId() != null) {
                            try {
                                account = adminController.getAccountById(admin, currentAlert.getAccountId());
                            } catch (Exception ignored) {}
                        }
                    }

                    List<TriageActionItem> actions = generateActions(currentAlert, account, alertIdx, allAlerts.size());
                    if (selectedIndex >= actions.size() && !actions.isEmpty()) {
                        selectedIndex = actions.size() - 1;
                    }

                    String rendered = renderContent(currentAlert, account, alertIdx, allAlerts.size(), selectedIndex, statusMessage, isError, width);
                    ScreenRenderer.render(rendered, firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && (event.ch() == 'b' || event.ch() == 'B'))) {
                        running = false;
                        navigator.pop();
                        return;
                    }

                    if (isEmptyState) {
                        if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'k' || event.ch() == 'K'))) {
                            selectedIndex = (selectedIndex - 1 + 2) % 2;
                        } else if (event.action() == KeyAction.DOWN || (event.action() == KeyAction.CHAR && (event.ch() == 'j' || event.ch() == 'J'))) {
                            selectedIndex = (selectedIndex + 1) % 2;
                        } else if (event.action() == KeyAction.CHAR && event.ch() == '1') {
                            selectedIndex = 0;
                            allAlerts = adminController.getAllFraudAlerts(admin);
                            if (allAlerts != null && !allAlerts.isEmpty()) {
                                alertIdx = 0;
                                currentAlert = allAlerts.get(0);
                                statusMessage = "Alert feed refreshed. " + allAlerts.size() + " incident(s) loaded.";
                                isError = false;
                            } else {
                                statusMessage = "Threat radar clear. Zero pending incidents in queue.";
                                isError = false;
                            }
                        } else if (event.action() == KeyAction.CHAR && event.ch() == '2') {
                            running = false;
                            navigator.pop();
                            return;
                        } else if (event.action() == KeyAction.ENTER) {
                            if (selectedIndex == 0) {
                                allAlerts = adminController.getAllFraudAlerts(admin);
                                if (allAlerts != null && !allAlerts.isEmpty()) {
                                    alertIdx = 0;
                                    currentAlert = allAlerts.get(0);
                                    statusMessage = "Alert feed refreshed. " + allAlerts.size() + " incident(s) loaded.";
                                    isError = false;
                                } else {
                                    statusMessage = "Threat radar clear. Zero pending incidents in queue.";
                                    isError = false;
                                }
                            } else {
                                running = false;
                                navigator.pop();
                                return;
                            }
                        }
                    } else {
                        int actionCount = actions.size();
                        if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'k' || event.ch() == 'K'))) {
                            if (actionCount > 0) {
                                selectedIndex = (selectedIndex - 1 + actionCount) % actionCount;
                            }
                        } else if (event.action() == KeyAction.DOWN || (event.action() == KeyAction.CHAR && (event.ch() == 'j' || event.ch() == 'J'))) {
                            if (actionCount > 0) {
                                selectedIndex = (selectedIndex + 1) % actionCount;
                            }
                        } else if (event.action() == KeyAction.LEFT || (event.action() == KeyAction.CHAR && (event.ch() == 'h' || event.ch() == 'H'))) {
                            if (!allAlerts.isEmpty()) {
                                alertIdx = (alertIdx - 1 + allAlerts.size()) % allAlerts.size();
                                currentAlert = allAlerts.get(alertIdx);
                                statusMessage = "Switched to incident #" + formatAlertId(currentAlert.getAlertId());
                                isError = false;
                                selectedIndex = 0;
                            }
                        } else if (event.action() == KeyAction.RIGHT || (event.action() == KeyAction.CHAR && (event.ch() == 'l' || event.ch() == 'L'))) {
                            if (!allAlerts.isEmpty()) {
                                alertIdx = (alertIdx + 1) % allAlerts.size();
                                currentAlert = allAlerts.get(alertIdx);
                                statusMessage = "Switched to incident #" + formatAlertId(currentAlert.getAlertId());
                                isError = false;
                                selectedIndex = 0;
                            }
                        } else if (event.action() == KeyAction.CHAR && (event.ch() == 'u' || event.ch() == 'U')) {
                            if (currentAlert != null && currentAlert.getUserId() != null) {
                                running = false;
                                navigator.push(new ComplianceUserForensicsScreen(adminController, currentAlert.getUserId()));
                                return;
                            } else {
                                statusMessage = "No user dossier linked to current alert.";
                                isError = true;
                            }
                        } else if (event.action() == KeyAction.CHAR && event.ch() >= '1' && event.ch() <= ('0' + Math.min(9, actionCount))) {
                            int opt = event.ch() - '1';
                            if (opt >= 0 && opt < actions.size()) {
                                selectedIndex = opt;
                                boolean exit = executeAction(actions.get(opt), admin, allAlerts, alertIdx, navigator);
                                if (exit) return;
                                if (actions.get(opt).getType() == ActionType.NEXT_INCIDENT && !allAlerts.isEmpty()) {
                                    alertIdx = (alertIdx + 1) % allAlerts.size();
                                    currentAlert = allAlerts.get(alertIdx);
                                    selectedIndex = 0;
                                }
                            }
                        } else if (event.action() == KeyAction.ENTER) {
                            if (selectedIndex >= 0 && selectedIndex < actions.size()) {
                                TriageActionItem chosen = actions.get(selectedIndex);
                                boolean exit = executeAction(chosen, admin, allAlerts, alertIdx, navigator);
                                if (exit) return;
                                if (chosen.getType() == ActionType.NEXT_INCIDENT && !allAlerts.isEmpty()) {
                                    alertIdx = (alertIdx + 1) % allAlerts.size();
                                    currentAlert = allAlerts.get(alertIdx);
                                    selectedIndex = 0;
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("FraudInvestigationScreen error recovery", ex);
                    statusMessage = "Status: Action completed or temporarily deferred. Press [Esc] to return.";
                    isError = true;
                }
            }
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private boolean executeAction(TriageActionItem action, Admin admin, List<FraudAlert> allAlerts, int alertIdx, ScreenNavigator navigator) {
        if (action == null || currentAlert == null) return false;

        String accStr = currentAlert.getAccountId() != null
                ? String.format("DGB-%09d", currentAlert.getAccountId()) : "N/A";
        String alertIdStr = formatAlertId(currentAlert.getAlertId());

        switch (action.getType()) {
            case FREEZE -> {
                if (currentAlert.getAccountId() != null) {
                    try {
                        adminController.freezeAccount(admin, currentAlert.getAccountId(), "Immediate Restriction - Fraud Triage");
                        statusMessage = "Status: Account " + accStr + " placed on restriction. Debit operations locked.";
                        isError = false;
                    } catch (Exception e) {
                        statusMessage = "Freeze failed: " + e.getMessage();
                        isError = true;
                    }
                } else {
                    statusMessage = "No linked account to freeze.";
                    isError = true;
                }
                return false;
            }
            case UNFREEZE -> {
                if (currentAlert.getAccountId() != null) {
                    try {
                        adminController.unfreezeAccount(admin, currentAlert.getAccountId());
                        statusMessage = "Status: Account " + accStr + " restored to full operational standing.";
                        isError = false;
                    } catch (Exception e) {
                        statusMessage = "Unfreeze failed: " + e.getMessage();
                        isError = true;
                    }
                } else {
                    statusMessage = "No linked account to unfreeze.";
                    isError = true;
                }
                return false;
            }
            case INVESTIGATE -> {
                try {
                    adminController.investigateAlert(admin, currentAlert.getAlertId());
                    currentAlert.setStatus(FraudStatus.UNDER_INVESTIGATION);
                    statusMessage = "Incident " + alertIdStr + " successfully set to UNDER_INVESTIGATION.";
                    isError = false;
                } catch (Exception e) {
                    statusMessage = "Investigation failed: " + e.getMessage();
                    isError = true;
                }
                return false;
            }
            case CONFIRM_FRAUD -> {
                try {
                    adminController.resolveAlert(admin, currentAlert.getAlertId(), "Confirmed fraud via compliance triage", true);
                    currentAlert.setStatus(FraudStatus.CONFIRMED_FRAUD);
                    statusMessage = "Incident " + alertIdStr + " marked as Confirmed Fraud. Audit trail committed.";
                    isError = false;
                } catch (Exception e) {
                    statusMessage = "Confirmation failed: " + e.getMessage();
                    isError = true;
                }
                return false;
            }
            case DISMISS_FALSE_POSITIVE -> {
                try {
                    adminController.resolveAlert(admin, currentAlert.getAlertId(), "Dismissed as false positive", false);
                    currentAlert.setStatus(FraudStatus.RESOLVED);
                    statusMessage = "Incident " + alertIdStr + " dismissed as False Positive. Flag cleared.";
                    isError = false;
                } catch (Exception e) {
                    statusMessage = "Dismissal failed: " + e.getMessage();
                    isError = true;
                }
                return false;
            }
            case NEXT_INCIDENT -> {
                if (!allAlerts.isEmpty()) {
                    int next = (alertIdx + 1) % allAlerts.size();
                    statusMessage = "Switched to incident #" + formatAlertId(allAlerts.get(next).getAlertId());
                    isError = false;
                }
                return false;
            }
            case BACK_DASHBOARD -> {
                navigator.pop();
                return true;
            }
            case VIEW_USER_FORENSICS -> {
                if (currentAlert.getUserId() != null) {
                    navigator.push(new ComplianceUserForensicsScreen(adminController, currentAlert.getUserId()));
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public static List<TriageActionItem> generateActions(FraudAlert alert, Account account, int currentIdx, int totalAlerts) {
        List<TriageActionItem> actions = new ArrayList<>();
        if (alert == null) return actions;

        boolean isResolved = (alert.getStatus() == FraudStatus.RESOLVED || alert.getStatus() == FraudStatus.CONFIRMED_FRAUD);

        if (!isResolved) {
            String accStatus = (account != null && account.getStatus() != null)
                    ? account.getStatus().name() : "ACTIVE";

            if ("ACTIVE".equalsIgnoreCase(accStatus)) {
                actions.add(new TriageActionItem(ActionType.FREEZE, "Freeze Compromised Account (Immediate Restriction)"));
            } else if ("FROZEN".equalsIgnoreCase(accStatus)) {
                actions.add(new TriageActionItem(ActionType.UNFREEZE, "Unfreeze Account (Restore Standing)"));
            }

            boolean isPending = (alert.getStatus() == FraudStatus.OPEN || alert.getStatus() == FraudStatus.PENDING);
            boolean isUnderInvestigation = (alert.getStatus() == FraudStatus.INVESTIGATING || alert.getStatus() == FraudStatus.UNDER_INVESTIGATION);

            if (isPending) {
                actions.add(new TriageActionItem(ActionType.INVESTIGATE, "Mark as Under Investigation"));
            } else if (isUnderInvestigation) {
                actions.add(new TriageActionItem(ActionType.CONFIRM_FRAUD, "Mark Incident as Confirmed Fraud (Audit Trail Committal)"));
                actions.add(new TriageActionItem(ActionType.DISMISS_FALSE_POSITIVE, "Dismiss Alert as False Positive (Clear Flag)"));
            }

            if (totalAlerts > 1) {
                actions.add(new TriageActionItem(ActionType.NEXT_INCIDENT, "Next Incident Dossier (→)"));
            }
        } else {
            if (totalAlerts > 1) {
                actions.add(new TriageActionItem(ActionType.NEXT_INCIDENT, "Next Incident Dossier (→)"));
            }
            actions.add(new TriageActionItem(ActionType.BACK_DASHBOARD, "Back to Dashboard"));
        }

        return actions;
    }

    public static String renderContent(FraudAlert alert, int selectedIndex, String statusMessage, boolean isError, int width) {
        Account acc = null;
        if (alert != null && alert.getAccountId() != null) {
            try {
                acc = ControllerFactory.getAccountRepository().findById(alert.getAccountId()).orElse(null);
            } catch (Exception ignored) {}
        }
        return renderContent(alert, acc, 0, 1, selectedIndex, statusMessage, isError, width);
    }

    public static String renderContent(FraudAlert alert, Account account, int currentIdx, int totalAlerts,
                                       int selectedIndex, String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();

        if (alert == null) {
            sb.append(TUIBox.top(width)).append("\n");
            sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > FRAUD DETECTION & THREAT TRIAGE"), width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");

            sb.append(TUIBox.line(" " + ConsoleTheme.bold("ACTIVE INCIDENT DOSSIER"), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");

            String checkMsg = "✓ NO ACTIVE FRAUD ALERTS REQUIRING ATTENTION";
            sb.append(TUIBox.center(ConsoleTheme.success(checkMsg), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");

            String descMsg = "All account transaction streams are nominal. Integrity OK.";
            sb.append(TUIBox.center(ConsoleTheme.muted(descMsg), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");

            sb.append(TUIBox.line(" " + ConsoleTheme.bold("AVAILABLE ACTIONS"), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");

            renderTriageAction(sb, 1, "Refresh Alert Feed", selectedIndex == 0, width);
            renderTriageAction(sb, 2, "Return to Administrative Console", selectedIndex == 1, width);

            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");

            String defaultStatus = (statusMessage != null && !statusMessage.isEmpty() && !statusMessage.startsWith("Ready."))
                    ? statusMessage : "Threat radar clear. Zero pending incidents in queue.";
            if (defaultStatus.startsWith("Status: ")) defaultStatus = defaultStatus.substring(8);
            if (defaultStatus.length() > 68) defaultStatus = defaultStatus.substring(0, 65) + "...";
            String statusDisplay = isError ? ConsoleTheme.error(defaultStatus) : ConsoleTheme.muted(defaultStatus);
            sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
            sb.append(TUIBox.bottom(width)).append("\n");

            sb.append(ConsoleTheme.keyGuide("[↑/↓] Navigate  •  [Enter] Select  •  [1-2] Hotkey  •  [Esc] Back")).append("\n");

            return sb.toString();
        }

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > FRAUD DETECTION & THREAT TRIAGE"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        String alertIdStr = formatAlertId(alert.getAlertId());
        String accStr = (alert.getAccountId() != null)
                ? String.format("DGB-%09d", alert.getAccountId()) : "DGB-000000000";
        String txnStr = (alert.getTransactionId() != null)
                ? "#TX-" + alert.getTransactionId() : "#TX-NONE";

        String accStatus = (account != null && account.getStatus() != null)
                ? account.getStatus().name() : "ACTIVE";

        String incidentStatus;
        if (alert.getStatus() == FraudStatus.INVESTIGATING || alert.getStatus() == FraudStatus.UNDER_INVESTIGATION) {
            incidentStatus = "UNDER_INVESTIGATION";
        } else if (alert.getStatus() == FraudStatus.OPEN || alert.getStatus() == FraudStatus.PENDING) {
            incidentStatus = "PENDING";
        } else {
            incidentStatus = alert.getStatus() != null ? alert.getStatus().name() : "OPEN";
        }

        String riskScoreStr = (alert.getRiskLevel() == RiskLevel.HIGH) ? "HIGH (92/100)"
                : (alert.getRiskLevel() == RiskLevel.MEDIUM) ? "MEDIUM (65/100)" : "LOW (15/100)";

        String reasonStr = alert.getDescription() != null ? alert.getDescription() : "High-value velocity threshold exceeded";
        if (reasonStr.length() > 53) reasonStr = reasonStr.substring(0, 50) + "...";

        // Dossier header row with QUEUE indicator
        String dossierLeft = " " + ConsoleTheme.bold("ACTIVE INCIDENT DOSSIER [" + alertIdStr + "]");
        String dossierRight = "QUEUE: " + (currentIdx + 1) + " OF " + Math.max(1, totalAlerts) + " INCIDENTS ";
        sb.append(TUIBox.twoColumns(dossierLeft, dossierRight, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Metadata rows
        String r1 = String.format("  Alert Identifier : %-22s Flagged Account : %s", alertIdStr, accStr);
        String r2 = String.format("  Transaction Ref  : %-22s Account Status  : %s", txnStr, accStatus);
        String r3 = String.format("  Incident Status  : %-22s Risk Assessment : %s", incidentStatus, riskScoreStr);
        String r4 = String.format("  Flag Reason      : %s", reasonStr);

        sb.append(TUIBox.line(r1, width)).append("\n");
        sb.append(TUIBox.line(r2, width)).append("\n");
        sb.append(TUIBox.line(r3, width)).append("\n");
        sb.append(TUIBox.line(r4, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Triage actions header
        String actionHeader = "TRIAGE ACTIONS FOR " + accStr;
        sb.append(TUIBox.line(" " + ConsoleTheme.bold(actionHeader), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        List<TriageActionItem> actions = generateActions(alert, account, currentIdx, totalAlerts);
        for (int i = 0; i < actions.size(); i++) {
            renderTriageAction(sb, i + 1, actions.get(i).getLabel(), i == selectedIndex, width);
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        String defaultStatus;
        if (statusMessage != null && !statusMessage.isEmpty() && !statusMessage.startsWith("Ready.")) {
            defaultStatus = statusMessage;
        } else {
            if ("ACTIVE".equalsIgnoreCase(accStatus)) {
                defaultStatus = "Status: Account is operational. Press [1] to restrict debit operations.";
            } else {
                defaultStatus = "Status: Account is restricted. Press [1] to restore full operations.";
            }
        }
        if (defaultStatus.length() > 70) defaultStatus = defaultStatus.substring(0, 67) + "...";
        String statusDisplay = isError ? ConsoleTheme.error(defaultStatus) : ConsoleTheme.muted(defaultStatus);
        sb.append(TUIBox.line(statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        int maxHotkey = Math.max(1, actions.size());
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Select Action  •  [Enter] Execute  •  [1-" + maxHotkey + "] Hotkey  •  [Esc] Exit Triage")).append("\n");

        return sb.toString();
    }

    private void renderTriageAction(int key, String label, boolean isSelected) {
        String prefix = isSelected ? "▸ " : "  ";
        String lineContent = String.format("%s[%d] %s", prefix, key, label);

        // Strict clamp to 74 visible chars
        lineContent = String.format("%-74s", lineContent);

        if (isSelected) {
            System.out.printf("│  \033[7m%s\033[0m  │%n", lineContent);
        } else {
            System.out.printf("│  %s  │%n", lineContent);
        }
    }

    private static void renderTriageAction(StringBuilder sb, int key, String label, boolean isSelected, int width) {
        String prefix = isSelected ? "▸ " : "  ";
        String lineContent = String.format("%s[%d] %s", prefix, key, label);

        // Strict clamp to 74 visible chars
        if (lineContent.length() > 74) {
            lineContent = lineContent.substring(0, 74);
        } else {
            lineContent = String.format("%-74s", lineContent);
        }

        if (isSelected) {
            sb.append(TUIBox.line("  \033[7m" + lineContent + "\033[0m", width)).append("\n");
        } else {
            sb.append(TUIBox.line("  " + lineContent, width)).append("\n");
        }
    }

    private static String formatAlertId(Long id) {
        return id != null ? String.format("#ALT-%02d", id) : "#ALT-00";
    }
}
