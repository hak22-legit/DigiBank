package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.model.dto.AdminDTO;
import com.bank.model.entity.Admin;
import com.bank.model.entity.FraudAlert;
import com.bank.model.enums.RiskLevel;
import com.bank.security.SessionManager;

import java.util.List;

/**
 * Screen for managing and investigating fraud alerts (Compliance Officer / Super Admin).
 */
public class AdminFraudScreen implements Screen {
    private final AdminController adminController;
    private String statusMessage;
    private boolean isErrorStatus;

    public AdminFraudScreen() {
        this(ControllerFactory.getAdminController());
    }

    public AdminFraudScreen(AdminController adminController) {
        this.adminController = adminController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        AdminDTO adminDto = session.getCurrentAdmin();
        Admin adminEntity = SessionManager.getCurrentAdmin();
        if (adminDto == null || adminEntity == null) {
            navigator.pop();
            return;
        }

        session.clearScreen();
        TUILayout.printHeader("Compliance: " + adminDto.getUsername() + " (" + adminDto.getRole() + ")");
        TUILayout.printScreenTitle("Fraud Detection & Alert Investigations");

        if (statusMessage != null) {
            TUILayout.printAlert(statusMessage, isErrorStatus);
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            statusMessage = null;
        }

        List<FraudAlert> alerts = null;
        try {
            alerts = adminController.getAllFraudAlerts(adminEntity);
        } catch (Exception e) {
            TUILayout.printAlert("Failed to load fraud alerts: " + e.getMessage(), true);
        }

        ConsoleTable table = new ConsoleTable()
                .addColumn("Alert ID", false)
                .addColumn("Account", false)
                .addColumn("Txn ID", false)
                .addColumn("Risk", false)
                .addColumn("Status", false)
                .addColumn("Description", false);

        if (alerts != null && !alerts.isEmpty()) {
            for (FraudAlert a : alerts) {
                String id = String.valueOf(a.getAlertId());
                String acc = a.getAccountId() != null ? String.valueOf(a.getAccountId()) : "-";
                String tx = a.getTransactionId() != null ? String.valueOf(a.getTransactionId()) : "-";

                String risk = a.getRiskLevel() == RiskLevel.HIGH
                        ? ConsoleTheme.error("HIGH")
                        : (a.getRiskLevel() == RiskLevel.MEDIUM ? ConsoleTheme.warning("MED") : ConsoleTheme.success("LOW"));

                String stat = switch (a.getStatus()) {
                    case OPEN -> ConsoleTheme.error("OPEN");
                    case INVESTIGATING -> ConsoleTheme.warning("INVESTIGATING");
                    case RESOLVED -> ConsoleTheme.success("RESOLVED");
                    case CONFIRMED_FRAUD -> ConsoleTheme.error("CONFIRMED_FRAUD");
                };

                String desc = a.getDescription() != null ? a.getDescription() : "-";
                if (desc.length() > 24) desc = desc.substring(0, 21) + "...";

                table.addRow(id, acc, tx, risk, stat, desc);
            }
            table.print();
        } else {
            System.out.println(TUIBox.center(ConsoleTheme.muted("No fraud alerts recorded in the system. All systems normal."), TUILayout.APP_WIDTH));
        }

        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

        ConsoleMenu menu = new ConsoleMenu()
                .addItem("01", "Investigate Alert", "Mark open alert as actively being investigated")
                .addItem("02", "Resolve Alert", "Close alert with notes as resolved or confirmed fraud")
                .addItem("03", "Freeze Account", "Restrict compromised account immediately")
                .addItem("04", "Unfreeze Account", "Restore normal account status after review")
                .addItem("05", "Back to Admin Menu", "Return to main staff console");

        ConsoleMenu.MenuItem selected = menu.select();
        if (selected == null || "05".equals(selected.getCode())) {
            navigator.pop();
            return;
        }

        switch (selected.getCode()) {
            case "01" -> handleInvestigate(adminEntity);
            case "02" -> handleResolve(adminEntity);
            case "03" -> handleFreeze(adminEntity);
            case "04" -> handleUnfreeze(adminEntity);
        }
    }

    private void handleInvestigate(Admin adminEntity) {
        String alertIdStr = ConsolePrompt.promptText("Alert ID to investigate");
        try {
            long alertId = Long.parseLong(alertIdStr.trim());
            adminController.investigateAlert(adminEntity, alertId);
            this.statusMessage = "Alert #" + alertId + " is now under active investigation.";
            this.isErrorStatus = false;
        } catch (Exception e) {
            this.statusMessage = "Investigation update failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }

    private void handleResolve(Admin adminEntity) {
        String alertIdStr = ConsolePrompt.promptText("Alert ID to resolve");
        long alertId;
        try {
            alertId = Long.parseLong(alertIdStr.trim());
        } catch (Exception e) {
            this.statusMessage = "Invalid alert ID.";
            this.isErrorStatus = true;
            return;
        }

        String notes = ConsolePrompt.promptText("Resolution Notes");
        boolean confirmed = ConsolePrompt.promptConfirmation("Confirm as ACTUAL FRAUD? [y: Confirmed Fraud, N: Resolved / False Alarm]");

        try {
            adminController.resolveAlert(adminEntity, alertId, notes, confirmed);
            this.statusMessage = "Alert #" + alertId + " successfully marked as " + (confirmed ? "CONFIRMED_FRAUD" : "RESOLVED") + ".";
            this.isErrorStatus = false;
        } catch (Exception e) {
            this.statusMessage = "Resolution failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }

    private void handleFreeze(Admin adminEntity) {
        String accIdStr = ConsolePrompt.promptText("Account ID to Freeze");
        long accId;
        try {
            accId = Long.parseLong(accIdStr.trim());
        } catch (Exception e) {
            this.statusMessage = "Invalid account ID.";
            this.isErrorStatus = true;
            return;
        }

        String reason = ConsolePrompt.promptText("Freeze Reason");
        boolean confirm = ConsolePrompt.promptConfirmation("Confirm FREEZING Account #" + accId + "?");
        if (!confirm) {
            this.statusMessage = "Operation cancelled.";
            this.isErrorStatus = false;
            return;
        }

        try {
            adminController.freezeAccount(adminEntity, accId, reason);
            this.statusMessage = "Account #" + accId + " is now FROZEN.";
            this.isErrorStatus = false;
        } catch (Exception e) {
            this.statusMessage = "Freeze failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }

    private void handleUnfreeze(Admin adminEntity) {
        String accIdStr = ConsolePrompt.promptText("Account ID to Unfreeze");
        long accId;
        try {
            accId = Long.parseLong(accIdStr.trim());
        } catch (Exception e) {
            this.statusMessage = "Invalid account ID.";
            this.isErrorStatus = true;
            return;
        }

        boolean confirm = ConsolePrompt.promptConfirmation("Confirm UNFREEZING Account #" + accId + "?");
        if (!confirm) {
            this.statusMessage = "Operation cancelled.";
            this.isErrorStatus = false;
            return;
        }

        try {
            adminController.unfreezeAccount(adminEntity, accId);
            this.statusMessage = "Account #" + accId + " unfreezed and restored to ACTIVE.";
            this.isErrorStatus = false;
        } catch (Exception e) {
            this.statusMessage = "Unfreeze failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }
}
