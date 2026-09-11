package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.model.PagedResult;
import com.bank.model.dto.AdminDTO;
import com.bank.model.entity.Admin;
import com.bank.model.entity.AuditLog;
import com.bank.security.SessionManager;

import java.time.format.DateTimeFormatter;

/**
 * Screen for viewing immutable administrative audit logs (Super Admin / Compliance Officer).
 */
public class AdminAuditLogScreen implements Screen {
    private final AdminController adminController;
    private int currentPage = 1;
    private final int pageSize = 10;

    public AdminAuditLogScreen() {
        this(ControllerFactory.getAdminController());
    }

    public AdminAuditLogScreen(AdminController adminController) {
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

        ConsoleMenu menu = new ConsoleMenu()
                .setHeaderSubtitle("Compliance Audit")
                .setScreenTitle("System Audit Logs — Page " + currentPage);

        PagedResult<AuditLog> result = null;
        try {
            result = adminController.getAuditLogs(adminEntity, currentPage, pageSize);
        } catch (Exception e) {
            menu.setAlert("Failed to load audit logs: " + e.getMessage(), true);
        }

        ConsoleTable table = new ConsoleTable()
                .addColumn("Timestamp", false)
                .addColumn("Admin", false)
                .addColumn("Action", false)
                .addColumn("Target", false)
                .addColumn("Details", false);

        if (result != null && result.getItems() != null && !result.getItems().isEmpty()) {
            for (AuditLog log : result.getItems()) {
                String time = log.getCreatedAt() != null
                        ? log.getCreatedAt().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                        : "-";
                String adm = "Admin #" + log.getAdminId();
                String action = ConsoleTheme.highlight(log.getAction());
                String target = (log.getTargetTable() != null ? log.getTargetTable() : "") +
                        (log.getTargetId() != null ? ":" + log.getTargetId() : "");
                String details = log.getDetails() != null ? log.getDetails() : "-";
                if (details.length() > 30) details = details.substring(0, 27) + "...";

                table.addRow(time, adm, action, target, details);
            }
            menu.setCustomContent(table.render());
        } else {
            menu.setCustomContent(TUIBox.center(ConsoleTheme.muted("No audit logs found on this page."), TUILayout.APP_WIDTH) + "\n");
        }

        if (result != null && result.hasNextPage()) {
            menu.addItem("01", "Next Page", "View page " + (currentPage + 1));
        }
        if (result != null && result.hasPreviousPage()) {
            menu.addItem("02", "Previous Page", "View page " + (currentPage - 1));
        }
        menu.addItem("03", "Back to Admin Menu", "Return to main staff console");

        ConsoleMenu.MenuItem selected = menu.select();
        if (selected == null || "03".equals(selected.getCode())) {
            navigator.pop();
            return;
        }

        if ("01".equals(selected.getCode())) {
            currentPage++;
        } else if ("02".equals(selected.getCode())) {
            currentPage = Math.max(1, currentPage - 1);
        }
    }
}
