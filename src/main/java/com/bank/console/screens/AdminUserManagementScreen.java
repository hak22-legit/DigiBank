package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.model.dto.AdminDTO;
import com.bank.model.entity.Admin;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;

import java.util.List;

/**
 * Screen for managing and inspecting customer accounts (Super Admin only).
 */
public class AdminUserManagementScreen implements Screen {
    private final AdminController adminController;

    public AdminUserManagementScreen() {
        this(ControllerFactory.getAdminController());
    }

    public AdminUserManagementScreen(AdminController adminController) {
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

        if (adminDto.getRole() == com.bank.model.enums.AdminRole.SUPER_ADMIN) {
            navigator.replace(new StaffManagementScreen());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(TUILayout.header("Staff: " + adminDto.getUsername() + " (" + adminDto.getRole() + ")"));
        sb.append(TUILayout.screenTitle("Customer Profile Management"));

        try {
            List<User> users = adminController.getAllUsers(adminEntity);

            ConsoleTable table = new ConsoleTable()
                    .addColumn("User ID", false)
                    .addColumn("Username", false)
                    .addColumn("Full Name", false)
                    .addColumn("Email", false)
                    .addColumn("Phone", false)
                    .addColumn("Status", false);

            if (users != null && !users.isEmpty()) {
                for (User u : users) {
                    String id = String.valueOf(u.getUserId());
                    String uname = u.getUsername();
                    String name = u.getFullName();
                    String email = u.getEmail();
                    String phone = u.getPhone() != null ? u.getPhone() : "-";
                    String status = "ACTIVE".equalsIgnoreCase(String.valueOf(u.getStatus()))
                            ? ConsoleTheme.success("ACTIVE")
                            : ConsoleTheme.warning(String.valueOf(u.getStatus()));

                    table.addRow(id, uname, name, email, phone, status);
                }
                sb.append(table.render());
            } else {
                sb.append(TUIBox.center(ConsoleTheme.muted("No customer accounts registered in the database."), TUILayout.APP_WIDTH)).append("\n");
            }
        } catch (Exception e) {
            sb.append(TUILayout.alert("Failed to load user directory: " + e.getMessage(), true));
        }

        sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUILayout.footer("Press Enter to return to Admin Menu"));
        ScreenRenderer.render(sb.toString(), true);
        ConsolePrompt.pause();
        navigator.pop();
    }
}
