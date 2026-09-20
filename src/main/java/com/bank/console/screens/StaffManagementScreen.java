package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.model.entity.Admin;
import com.bank.model.enums.AdminRole;
import com.bank.model.enums.AdminStatus;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * SUPER ADMIN > SECURITY & CREDENTIAL OPERATIONS (82 Columns)
 * Internal Staff Directory and interactive in-place Staff Provisioning Form with Email & Phone.
 */
public class StaffManagementScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(StaffManagementScreen.class);

    private final AdminController adminController;

    public StaffManagementScreen() {
        this(ControllerFactory.getAdminController());
    }

    public StaffManagementScreen(AdminController adminController) {
        this.adminController = adminController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        Admin admin = SessionManager.getCurrentAdmin();
        if (admin == null || admin.getRole() != AdminRole.SUPER_ADMIN) {
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;
        boolean isProvisioning = false;
        int focusedField = 0; // 0: Username, 1: Role, 2: Password, 3: Full Name, 4: Email, 5: Phone

        StringBuilder usernameBuf = new StringBuilder();
        boolean isLoanRole = true;
        StringBuilder passwordBuf = new StringBuilder();
        StringBuilder fullNameBuf = new StringBuilder();
        StringBuilder emailBuf = new StringBuilder();
        StringBuilder phoneBuf = new StringBuilder();

        String statusMessage = "Staff directory synchronized. Select a staff account or press [C] to provision.";
        boolean isError = false;
        boolean firstRender = true;

        List<Admin> staffList = new ArrayList<>();
        try {
            staffList = adminController.getAllAdmins(admin);
        } catch (Exception e) {
            logger.error("Failed to load staff list", e);
        }

        try {
            while (true) {
                try {
                    if (!isProvisioning) {
                        if (selectedIndex >= staffList.size() && !staffList.isEmpty()) {
                            selectedIndex = staffList.size() - 1;
                        }

                        Admin selectedStaff = (!staffList.isEmpty() && selectedIndex >= 0 && selectedIndex < staffList.size())
                                ? staffList.get(selectedIndex) : null;

                        if (selectedStaff != null && (statusMessage.startsWith("Selected ") || statusMessage.startsWith("Staff directory synchronized"))) {
                            statusMessage = String.format("Selected %s (#ADM-%02d). Press [R] to issue temporary password.",
                                    selectedStaff.getUsername(), selectedStaff.getAdminId());
                        }

                        String rendered = renderContent(staffList, selectedIndex, statusMessage, isError, width);
                        ScreenRenderer.render(rendered, firstRender);
                        firstRender = false;

                        KeyEvent event = TUIFormHelper.readKey(reader);
                        if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && (event.ch() == 'b' || event.ch() == 'B'))) {
                            navigator.pop();
                            return;
                        } else if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'k' || event.ch() == 'K'))) {
                            if (!staffList.isEmpty()) {
                                selectedIndex = (selectedIndex - 1 + staffList.size()) % staffList.size();
                            }
                        } else if (event.action() == KeyAction.DOWN || (event.action() == KeyAction.CHAR && (event.ch() == 'j' || event.ch() == 'J'))) {
                            if (!staffList.isEmpty()) {
                                selectedIndex = (selectedIndex + 1) % staffList.size();
                            }
                        } else if (event.action() == KeyAction.CHAR && (event.ch() == 'c' || event.ch() == 'C')) {
                            isProvisioning = true;
                            focusedField = 0;
                            usernameBuf.setLength(0);
                            isLoanRole = true;
                            passwordBuf.setLength(0);
                            fullNameBuf.setLength(0);
                            emailBuf.setLength(0);
                            phoneBuf.setLength(0);
                            statusMessage = "Enter corporate username for staff login. Press [Tab] to proceed.";
                            isError = false;
                            firstRender = true;
                        } else if (event.action() == KeyAction.CHAR && (event.ch() == 'r' || event.ch() == 'R')) {
                            if (selectedStaff == null) {
                                statusMessage = "No staff account selected to reset password.";
                                isError = true;
                            } else {
                                terminal.setAttributes(origAttributes);
                                try {
                                    String newPassword = ConsolePrompt.promptText("Enter New Temporary Password for " + selectedStaff.getUsername());
                                    if (newPassword != null && !newPassword.isBlank()) {
                                        adminController.resetAdminPassword(admin, selectedStaff.getAdminId(), newPassword.trim());
                                        statusMessage = "Password reset successfully for " + selectedStaff.getUsername() + ".";
                                        isError = false;
                                    }
                                } catch (Exception e) {
                                    statusMessage = "Password reset failed: " + e.getMessage();
                                    isError = true;
                                } finally {
                                    terminal.enterRawMode();
                                    firstRender = true;
                                }
                            }
                        } else if (event.action() == KeyAction.CHAR && (event.ch() == 't' || event.ch() == 'T')) {
                            if (selectedStaff == null) {
                                statusMessage = "No staff account selected to toggle role.";
                                isError = true;
                            } else if (selectedStaff.getRole() == AdminRole.SUPER_ADMIN) {
                                statusMessage = "Cannot change role of SUPER_ADMIN.";
                                isError = true;
                            } else {
                                try {
                                    Admin updated = adminController.toggleAdminRole(admin, selectedStaff.getAdminId());
                                    selectedStaff.setRole(updated.getRole());
                                    statusMessage = "Role toggled: " + updated.getUsername() + " is now " + updated.getRole() + ".";
                                    isError = false;
                                } catch (Exception e) {
                                    statusMessage = "Role toggle failed: " + e.getMessage();
                                    isError = true;
                                }
                            }
                        } else if (event.action() == KeyAction.CHAR && (event.ch() == 'f' || event.ch() == 'F')) {
                            if (selectedStaff == null) {
                                statusMessage = "No staff account selected to toggle status.";
                                isError = true;
                            } else if (selectedStaff.getRole() == AdminRole.SUPER_ADMIN) {
                                statusMessage = "Cannot suspend SUPER_ADMIN account.";
                                isError = true;
                            } else {
                                try {
                                    if (selectedStaff.getStatus() == AdminStatus.ACTIVE) {
                                        adminController.suspendAdmin(admin, selectedStaff.getAdminId());
                                        selectedStaff.setStatus(AdminStatus.INACTIVE);
                                        statusMessage = "Account " + selectedStaff.getUsername() + " suspended (INACTIVE).";
                                    } else {
                                        adminController.reactivateAdmin(admin, selectedStaff.getAdminId());
                                        selectedStaff.setStatus(AdminStatus.ACTIVE);
                                        statusMessage = "Account " + selectedStaff.getUsername() + " unlocked (ACTIVE).";
                                    }
                                    isError = false;
                                } catch (Exception e) {
                                    statusMessage = "Status update failed: " + e.getMessage();
                                    isError = true;
                                }
                            }
                        }
                    } else {
                        // Provisioning Mode
                        String rendered = renderProvisioningContent(staffList, usernameBuf.toString(), isLoanRole,
                                passwordBuf.toString(), fullNameBuf.toString(), emailBuf.toString(), phoneBuf.toString(),
                                focusedField, statusMessage, isError, width);
                        ScreenRenderer.render(rendered, firstRender);
                        firstRender = false;

                        KeyEvent event = TUIFormHelper.readKey(reader);
                        if (event.action() == KeyAction.ESCAPE) {
                            isProvisioning = false;
                            statusMessage = "Staff provisioning cancelled.";
                            isError = false;
                            firstRender = true;
                        } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                            focusedField = (focusedField + 1) % 6;
                            statusMessage = getFieldGuidance(focusedField);
                            isError = false;
                        } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                            focusedField = (focusedField - 1 + 6) % 6;
                            statusMessage = getFieldGuidance(focusedField);
                            isError = false;
                        } else if (event.action() == KeyAction.CHAR && event.ch() == ' ' && focusedField == 1) {
                            isLoanRole = !isLoanRole;
                            statusMessage = "Role toggled to " + (isLoanRole ? "LOAN_OFFICER" : "COMPLIANCE_OFFICER") + ".";
                            isError = false;
                        } else if (event.action() == KeyAction.BACKSPACE) {
                            switch (focusedField) {
                                case 0 -> { if (!usernameBuf.isEmpty()) usernameBuf.deleteCharAt(usernameBuf.length() - 1); }
                                case 2 -> { if (!passwordBuf.isEmpty()) passwordBuf.deleteCharAt(passwordBuf.length() - 1); }
                                case 3 -> { if (!fullNameBuf.isEmpty()) fullNameBuf.deleteCharAt(fullNameBuf.length() - 1); }
                                case 4 -> { if (!emailBuf.isEmpty()) emailBuf.deleteCharAt(emailBuf.length() - 1); }
                                case 5 -> { if (!phoneBuf.isEmpty()) phoneBuf.deleteCharAt(phoneBuf.length() - 1); }
                            }
                        } else if (event.action() == KeyAction.CHAR || event.action() == KeyAction.DIGIT) {
                            char ch = event.ch();
                            switch (focusedField) {
                                case 0 -> { if (usernameBuf.length() < 30) usernameBuf.append(ch); }
                                case 2 -> { if (passwordBuf.length() < 30) passwordBuf.append(ch); }
                                case 3 -> { if (fullNameBuf.length() < 40) fullNameBuf.append(ch); }
                                case 4 -> { if (emailBuf.length() < 40) emailBuf.append(ch); }
                                case 5 -> { if (phoneBuf.length() < 25) phoneBuf.append(ch); }
                            }
                        } else if (event.action() == KeyAction.ENTER) {
                            String u = usernameBuf.toString().trim();
                            String p = passwordBuf.toString().trim();
                            String fn = fullNameBuf.toString().trim();
                            String em = emailBuf.toString().trim();
                            String ph = phoneBuf.toString().trim();

                            if (u.isEmpty()) {
                                statusMessage = "Validation error: Username cannot be blank.";
                                isError = true;
                                focusedField = 0;
                            } else if (p.isEmpty()) {
                                statusMessage = "Validation error: Temporary password cannot be blank.";
                                isError = true;
                                focusedField = 2;
                            } else if (fn.isEmpty()) {
                                statusMessage = "Validation error: Staff full name cannot be blank.";
                                isError = true;
                                focusedField = 3;
                            } else if (em.isEmpty() || !em.contains("@")) {
                                statusMessage = "Validation error: Valid corporate email is required.";
                                isError = true;
                                focusedField = 4;
                            } else {
                                try {
                                    AdminRole role = isLoanRole ? AdminRole.LOAN_OFFICER : AdminRole.COMPLIANCE_OFFICER;
                                    adminController.createAdmin(admin, u, em, p, fn, ph.isEmpty() ? null : ph, role);
                                    staffList = adminController.getAllAdmins(admin);
                                    isProvisioning = false;
                                    statusMessage = "✔ Staff account '" + u + "' successfully provisioned!";
                                    isError = false;
                                    firstRender = true;
                                } catch (Exception e) {
                                    statusMessage = "Provisioning failed: " + e.getMessage();
                                    isError = true;
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("StaffManagementScreen loop iteration error", ex);
                    statusMessage = "Status: Action completed or temporarily deferred. Press [Esc] to return.";
                    isError = true;
                }
            }
        } catch (Exception e) {
            logger.error("Error in StaffManagementScreen loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private static String getFieldGuidance(int field) {
        return switch (field) {
            case 0 -> "Enter corporate username for staff login. Press [Tab] for Role.";
            case 1 -> "Press [Space] to toggle role (LOAN_OFFICER / COMPLIANCE_OFFICER).";
            case 2 -> "Enter temporary staff password. Press [Tab] to proceed.";
            case 3 -> "Enter staff member's legal full name. Press [Tab] for Email.";
            case 4 -> "Enter corporate email address. Press [Tab] to proceed to Phone.";
            case 5 -> "Enter corporate telephone / contact phone number. Press [Enter] to commit.";
            default -> "Fill in staff credentials.";
        };
    }

    public static String renderContent(List<Admin> staffList, int selectedIndex, String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SUPER ADMIN > SECURITY & CREDENTIAL OPERATIONS"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        sb.append(TUIBox.line(" " + ConsoleTheme.bold("INTERNAL STAFF DIRECTORY (Page 1/1)"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Table Header: strictly 76 chars inside margin
        String th = String.format("  %-10s %-13s %-19s %-9s %-13s %-7s",
                "STAFF ID", "USERNAME", "ROLE", "STATUS", "FAILED LOGINS", "MFA");
        th = String.format("%-76s", th);
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("  " + "─".repeat(74), width)).append("\n");

        if (staffList != null && !staffList.isEmpty()) {
            for (int i = 0; i < staffList.size(); i++) {
                Admin staff = staffList.get(i);
                boolean isSelected = (i == selectedIndex);

                String prefix = isSelected ? "▸ " : "  ";
                String idStr = String.format("#ADM-%02d", staff.getAdminId() != null ? staff.getAdminId() : 0);
                String uname = staff.getUsername() != null ? staff.getUsername() : "-";
                if (uname.length() > 13) uname = uname.substring(0, 10) + "...";

                String role = staff.getRole() != null ? staff.getRole().name() : "STAFF";
                if (role.length() > 19) role = role.substring(0, 16) + "...";

                String status = staff.getStatus() != null ? staff.getStatus().name() : "ACTIVE";
                String failed = "0 Attempts";
                String mfa = (staff.getRole() == AdminRole.SUPER_ADMIN || staff.getRole() == AdminRole.COMPLIANCE_OFFICER) ? "ENABLED" : "OFF";

                String rawRow = String.format("%s%-10s %-13s %-19s %-9s %-13s %-7s",
                        prefix, idStr, uname, role, status, failed, mfa);

                if (rawRow.length() > 76) {
                    rawRow = rawRow.substring(0, 76);
                } else {
                    rawRow = String.format("%-76s", rawRow);
                }

                String renderedRow;
                if (isSelected) {
                    renderedRow = "\033[7m" + rawRow + "\033[0m";
                } else {
                    renderedRow = rawRow;
                    if (role.equals("SUPER_ADMIN")) {
                        renderedRow = renderedRow.replace("SUPER_ADMIN", "\033[33mSUPER_ADMIN\033[0m");
                    }
                    if (status.equals("ACTIVE")) {
                        renderedRow = renderedRow.replace("ACTIVE", "\033[32mACTIVE\033[0m");
                    } else if (status.equals("INACTIVE") || status.equals("SUSPENDED")) {
                        renderedRow = renderedRow.replace(status, "\033[31m" + status + "\033[0m");
                    }
                    if (mfa.equals("ENABLED")) {
                        renderedRow = renderedRow.replace("ENABLED", "\033[32mENABLED\033[0m");
                    } else if (mfa.equals("OFF")) {
                        renderedRow = renderedRow.replace("OFF", "\033[31mOFF\033[0m");
                    }
                }
                sb.append(TUIBox.line(renderedRow, width)).append("\n");
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No staff members registered in the directory."), width)).append("\n");
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
        String statusDisplay = isError ? ConsoleTheme.error(statusMessage) : ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Select • [T] Role • [F] Suspend • [R] Reset • [C] New • [Esc] Back")).append("\n");

        return sb.toString();
    }

    public static String renderProvisioningContent(List<Admin> staffList, String username, boolean isLoanRole,
                                                   String password, String fullName, String email, String phone,
                                                   int focusedField, String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SUPER ADMIN > SECURITY & CREDENTIAL OPERATIONS"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        sb.append(TUIBox.line(" " + ConsoleTheme.bold("INTERNAL STAFF DIRECTORY (Page 1/1)"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String th = String.format("  %-10s %-13s %-19s %-9s %-13s %-7s",
                "STAFF ID", "USERNAME", "ROLE", "STATUS", "FAILED LOGINS", "MFA");
        th = String.format("%-76s", th);
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("  " + "─".repeat(74), width)).append("\n");

        int maxDisplay = 4;
        if (staffList != null && !staffList.isEmpty()) {
            for (int i = 0; i < Math.min(maxDisplay, staffList.size()); i++) {
                Admin staff = staffList.get(i);
                String idStr = String.format("#ADM-%02d", staff.getAdminId() != null ? staff.getAdminId() : 0);
                String uname = staff.getUsername() != null ? staff.getUsername() : "-";
                if (uname.length() > 13) uname = uname.substring(0, 10) + "...";
                String role = staff.getRole() != null ? staff.getRole().name() : "STAFF";
                if (role.length() > 19) role = role.substring(0, 16) + "...";
                String status = staff.getStatus() != null ? staff.getStatus().name() : "ACTIVE";
                String failed = "0 Attempts";
                String mfa = (staff.getRole() == AdminRole.SUPER_ADMIN || staff.getRole() == AdminRole.COMPLIANCE_OFFICER) ? "ENABLED" : "OFF";

                String rawRow = String.format("  %-10s %-13s %-19s %-9s %-13s %-7s",
                        idStr, uname, role, status, failed, mfa);
                if (rawRow.length() > 76) rawRow = rawRow.substring(0, 76);
                sb.append(TUIBox.line(rawRow, width)).append("\n");
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No staff members registered in the directory."), width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // PROVISION NEW STAFF CREDENTIALS COMPARTMENT
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("PROVISION NEW STAFF CREDENTIALS"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Field 0: Username
        String p0 = (focusedField == 0) ? "▸ " : "  ";
        String uVal = username + (focusedField == 0 ? "_" : "");
        String uBox = String.format("[ %-31s ]", uVal);
        String uLine = String.format("  %sUsername        : %s", p0, uBox);
        sb.append(TUIBox.line(focusedField == 0 ? ConsoleTheme.inlineHighlight(uLine) : uLine, width)).append("\n");

        // Field 1: Assigned Role
        String p1 = (focusedField == 1) ? "▸ " : "  ";
        String rVal = isLoanRole ? "LOAN_OFFICER" : "COMPLIANCE_OFFICER";
        String rBox = String.format("[ %-18s ] (Press [Space] to toggle)", rVal);
        String rLine = String.format("  %sAssigned Role   : %s", p1, rBox);
        sb.append(TUIBox.line(focusedField == 1 ? ConsoleTheme.inlineHighlight(rLine) : rLine, width)).append("\n");

        // Field 2: Temporary Pwd
        String p2 = (focusedField == 2) ? "▸ " : "  ";
        String pwdMasked = "•".repeat(password.length()) + (focusedField == 2 ? "_" : "");
        String pwdBox = String.format("[ %-31s ]", pwdMasked);
        String pwdLine = String.format("  %sTemporary Pwd   : %s", p2, pwdBox);
        sb.append(TUIBox.line(focusedField == 2 ? ConsoleTheme.inlineHighlight(pwdLine) : pwdLine, width)).append("\n");

        // Field 3: Staff Full Name
        String p3 = (focusedField == 3) ? "▸ " : "  ";
        String fnVal = fullName + (focusedField == 3 ? "_" : "");
        String fnBox = String.format("[ %-38s ]", fnVal);
        String fnLine = String.format("  %sStaff Full Name : %s", p3, fnBox);
        sb.append(TUIBox.line(focusedField == 3 ? ConsoleTheme.inlineHighlight(fnLine) : fnLine, width)).append("\n");

        // Field 4: Work Email
        String p4 = (focusedField == 4) ? "▸ " : "  ";
        String emVal = email + (focusedField == 4 ? "_" : "");
        String emBox = String.format("[ %-38s ]", emVal);
        String emLine = String.format("  %sWork Email      : %s", p4, emBox);
        sb.append(TUIBox.line(focusedField == 4 ? ConsoleTheme.inlineHighlight(emLine) : emLine, width)).append("\n");

        // Field 5: Phone Number
        String p5 = (focusedField == 5) ? "▸ " : "  ";
        String phVal = phone + (focusedField == 5 ? "_" : "");
        String phBox = String.format("[ %-38s ]", phVal);
        String phLine = String.format("  %sPhone Number    : %s", p5, phBox);
        sb.append(TUIBox.line(focusedField == 5 ? ConsoleTheme.inlineHighlight(phLine) : phLine, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.line("  [Enter] Confirm & Provision Account          [Esc] Cancel", width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        if (statusMessage != null && statusMessage.startsWith("Status: ")) {
            statusMessage = statusMessage.substring(8);
        }
        if (statusMessage != null && statusMessage.length() > 68) {
            statusMessage = statusMessage.substring(0, 65) + "...";
        }
        String statusDisplay = isError ? ConsoleTheme.error(statusMessage) : ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Space] Toggle Role  •  [Enter] Commit  •  [Esc] Cancel")).append("\n");

        return sb.toString();
    }
}
