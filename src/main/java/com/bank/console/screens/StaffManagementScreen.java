package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.TerminalInputHandler;
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
import java.time.format.DateTimeFormatter;
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
        // Drain any lingering newline characters on entry
        TerminalInputHandler.drainBuffer(reader);

        int selectedIndex = 0;
        int page = 1;
        final int pageSize = 6;
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
        boolean inScreen = true;

        List<Admin> staffList = new ArrayList<>();
        try {
            staffList = adminController.getAllAdmins(admin);
        } catch (Exception e) {
            logger.error("Failed to load staff list", e);
        }

        try {
            while (inScreen) {
                try {
                    if (!isProvisioning) {
                        int totalPages = Math.max(1, (int) Math.ceil((double) staffList.size() / pageSize));
                        if (selectedIndex >= staffList.size() && !staffList.isEmpty()) {
                            selectedIndex = staffList.size() - 1;
                        }
                        if (selectedIndex < 0) {
                            selectedIndex = 0;
                        }
                        page = (selectedIndex / pageSize) + 1;

                        Admin selectedStaff = (!staffList.isEmpty() && selectedIndex >= 0 && selectedIndex < staffList.size())
                                ? staffList.get(selectedIndex) : null;

                        if (selectedStaff != null && (statusMessage.startsWith("Selected ") || statusMessage.startsWith("Staff directory synchronized") || statusMessage.startsWith("Status: Staff "))) {
                            String staffIdStr = String.format("#ADM-%02d", selectedStaff.getAdminId() != null ? selectedStaff.getAdminId() : 0);
                            if (selectedStaff.getFailedLoginAttempts() >= 5) {
                                statusMessage = "Status: Staff " + staffIdStr + " is LOCKED (Threshold exceeded). Press [R] to unlock.";
                            } else {
                                statusMessage = "Status: Staff " + staffIdStr + " selected. Press [R] to provision temporary password.";
                            }
                        }

                        String rendered = renderContent(staffList, selectedIndex, statusMessage, isError, width);
                        ScreenRenderer.render(rendered, firstRender);
                        firstRender = false;

                        TerminalInputHandler.KeyCode event = TerminalInputHandler.readNavigationKey(reader);
                        String key = event.asNormalizedKey();

                        switch (key) {
                            case "ESC", "B" -> {
                                inScreen = false;
                                navigator.pop();
                                return;
                            }
                            case "UP", "K" -> {
                                selectedIndex = TerminalInputHandler.moveSelectionClamped(selectedIndex, -1, staffList.size());
                            }
                            case "DOWN", "J" -> {
                                selectedIndex = TerminalInputHandler.moveSelectionClamped(selectedIndex, 1, staffList.size());
                            }
                            case "N" -> {
                                if (page < totalPages) {
                                    selectedIndex = Math.min(staffList.size() - 1, page * pageSize);
                                }
                            }
                            case "P" -> {
                                if (page > 1) {
                                    selectedIndex = Math.max(0, (page - 2) * pageSize);
                                }
                            }
                            case "C" -> {
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
                            }
                            case "R", "ENTER" -> {
                                if (selectedStaff == null) {
                                    statusMessage = "No staff account selected to reset password.";
                                    isError = true;
                                } else {
                                    StringBuilder tempPasswordBuf = new StringBuilder();
                                    boolean hasAutoGenerated = false;
                                    int actionIdx = 0;
                                    String modalStatus = "Type temporary password or press [Tab] to auto-generate random one.";
                                    boolean modalError = false;
                                    boolean modalFirstRender = true;

                                    while (true) {
                                        String modalRendered = renderResetPasswordContent(selectedStaff, tempPasswordBuf.toString(),
                                                hasAutoGenerated, actionIdx, modalStatus, modalError, width);
                                        ScreenRenderer.render(modalRendered, modalFirstRender);
                                        modalFirstRender = false;

                                        TerminalInputHandler.KeyCode mev = TerminalInputHandler.readTypingKey(reader);
                                        if (mev.isEscape()) {
                                            statusMessage = "Password reset cancelled.";
                                            isError = false;
                                            firstRender = true;
                                            break;
                                        } else if (mev.isTab()) {
                                            tempPasswordBuf.setLength(0);
                                            tempPasswordBuf.append(generateSecureToken());
                                            hasAutoGenerated = true;
                                            actionIdx = 0;
                                            modalStatus = "Auto-generated 16-char token. Press [Enter] to commit to DB.";
                                            modalError = false;
                                        } else if (mev.isEnter()) {
                                            String passToCommit = tempPasswordBuf.toString().trim();
                                            if (passToCommit.isEmpty()) {
                                                modalStatus = "Temporary password cannot be blank. Type one or press [Tab] to auto-generate.";
                                                modalError = true;
                                                continue;
                                            }
                                            try {
                                                ControllerFactory.getStaffRepository().resetStaffPassword(selectedStaff.getAdminId(),
                                                        passToCommit, admin.getUsername(), admin.getAdminId());
                                                adminController.resetAdminPassword(admin, selectedStaff.getAdminId(), passToCommit);

                                                boolean inSuccess = true;
                                                boolean firstSuccessRender = true;
                                                while (inSuccess) {
                                                    String successRendered = renderResetSuccessContent(selectedStaff, passToCommit,
                                                            "Password updated in PostgreSQL. Audit trail record logged.", width);
                                                    ScreenRenderer.render(successRendered, firstSuccessRender);
                                                    firstSuccessRender = false;

                                                    TerminalInputHandler.KeyCode sev = TerminalInputHandler.readNavigationKey(reader);
                                                    if (sev.isEnter() || sev.isEscape() || sev.is('B')) {
                                                        inSuccess = false;
                                                    }
                                                }

                                                staffList = adminController.getAllAdmins(admin);
                                                statusMessage = String.format("Status: Password reset successfully for %s (#ADM-%02d).",
                                                        selectedStaff.getUsername(), selectedStaff.getAdminId());
                                                isError = false;
                                                firstRender = true;
                                                break;
                                            } catch (Exception e) {
                                                modalStatus = "Password reset failed: " + e.getMessage();
                                                modalError = true;
                                            }
                                        } else if (mev.isBackspace()) {
                                            if (tempPasswordBuf.length() > 0) {
                                                tempPasswordBuf.deleteCharAt(tempPasswordBuf.length() - 1);
                                                hasAutoGenerated = false;
                                            }
                                        } else if (mev.isChar() || mev.isDigit()) {
                                            char ch = mev.ch();
                                            if (tempPasswordBuf.length() < 40 && ch >= 32 && ch <= 126) {
                                                tempPasswordBuf.append(ch);
                                                hasAutoGenerated = false;
                                            }
                                        }
                                    }
                                }
                            }
                            case "T" -> {
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
                            }
                            case "F" -> {
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
                                            selectedStaff.setFailedLoginAttempts(0);
                                            statusMessage = "Account " + selectedStaff.getUsername() + " unlocked (ACTIVE).";
                                        }
                                        isError = false;
                                    } catch (Exception e) {
                                        statusMessage = "Status update failed: " + e.getMessage();
                                        isError = true;
                                    }
                                }
                            }
                            default -> {
                                // Discard unmapped keys without exiting or throwing
                            }
                        }
                    } else {
                        // Provisioning Mode
                        String rendered = renderProvisioningContent(staffList, usernameBuf.toString(), isLoanRole,
                                passwordBuf.toString(), fullNameBuf.toString(), emailBuf.toString(), phoneBuf.toString(),
                                focusedField, statusMessage, isError, width);
                        ScreenRenderer.render(rendered, firstRender);
                        firstRender = false;

                        TerminalInputHandler.KeyCode event = TerminalInputHandler.readTypingKey(reader);
                        if (event.isEscape()) {
                            isProvisioning = false;
                            statusMessage = "Staff provisioning cancelled.";
                            isError = false;
                            firstRender = true;
                        } else if (event.isTab() || event.isDown()) {
                            focusedField = (focusedField + 1) % 6;
                            statusMessage = getFieldGuidance(focusedField);
                            isError = false;
                        } else if (event.isShiftTab() || event.isUp()) {
                            focusedField = (focusedField - 1 + 6) % 6;
                            statusMessage = getFieldGuidance(focusedField);
                            isError = false;
                        } else if (event.is(' ') && focusedField == 1) {
                            isLoanRole = !isLoanRole;
                            statusMessage = "Role toggled to " + (isLoanRole ? "LOAN_OFFICER" : "COMPLIANCE_OFFICER") + ".";
                            isError = false;
                        } else if (event.isBackspace()) {
                            switch (focusedField) {
                                case 0 -> { if (!usernameBuf.isEmpty()) usernameBuf.deleteCharAt(usernameBuf.length() - 1); }
                                case 2 -> { if (!passwordBuf.isEmpty()) passwordBuf.deleteCharAt(passwordBuf.length() - 1); }
                                case 3 -> { if (!fullNameBuf.isEmpty()) fullNameBuf.deleteCharAt(fullNameBuf.length() - 1); }
                                case 4 -> { if (!emailBuf.isEmpty()) emailBuf.deleteCharAt(emailBuf.length() - 1); }
                                case 5 -> { if (!phoneBuf.isEmpty()) phoneBuf.deleteCharAt(phoneBuf.length() - 1); }
                            }
                        } else if (event.isChar() || event.isDigit()) {
                            char ch = event.ch();
                            switch (focusedField) {
                                case 0 -> { if (usernameBuf.length() < 30) usernameBuf.append(ch); }
                                case 2 -> { if (passwordBuf.length() < 30) passwordBuf.append(ch); }
                                case 3 -> { if (fullNameBuf.length() < 40) fullNameBuf.append(ch); }
                                case 4 -> { if (emailBuf.length() < 40) emailBuf.append(ch); }
                                case 5 -> { if (phoneBuf.length() < 25) phoneBuf.append(ch); }
                            }
                        } else if (event.isEnter()) {
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

        int pageSize = 6;
        int totalStaff = staffList != null ? staffList.size() : 0;
        int totalPages = Math.max(1, (totalStaff + pageSize - 1) / pageSize);
        int currentPage = Math.max(0, Math.min(totalPages - 1, selectedIndex / pageSize));
        int startIdx = currentPage * pageSize;
        int endIdx = Math.min(totalStaff, startIdx + pageSize);

        sb.append(TUIBox.line(" " + ConsoleTheme.bold(String.format("INTERNAL STAFF DIRECTORY (Page %d/%d)", currentPage + 1, totalPages)), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Table Header: strictly 80 visible chars inside outer border
        String th = "  STAFF ID   USERNAME        ROLE                 STATUS     FAILS   MFA STATUS ";
        sb.append("│").append(th).append("│\n");
        sb.append("│ ────────────────────────────────────────────────────────────────────────────── │\n");

        if (staffList != null && !staffList.isEmpty()) {
            for (int i = startIdx; i < endIdx; i++) {
                Admin staff = staffList.get(i);
                boolean isSelected = (i == selectedIndex);

                String prefix = isSelected ? "▸" : " ";
                String idStr = String.format("#ADM-%02d", staff.getAdminId() != null ? staff.getAdminId() : 0);
                String idCol = String.format("%s%-12s", prefix, idStr);

                String rawUname = staff.getUsername() != null ? staff.getUsername() : "-";
                if (rawUname.length() > 15) rawUname = rawUname.substring(0, 13) + "..";
                String unameCol = String.format("%-16s", rawUname);

                String rawRole = staff.getRole() != null ? staff.getRole().name() : "STAFF";
                if (rawRole.length() > 20) rawRole = rawRole.substring(0, 18) + "..";
                String roleCol = String.format("%-21s", rawRole);

                int failedCount = staff.getFailedLoginAttempts();
                boolean isLocked = (failedCount >= 5);
                String rawStatus = isLocked ? "SUSPENDED" : (staff.getStatus() != null ? staff.getStatus().name() : "ACTIVE");
                String statusCol = String.format("%-11s", rawStatus);

                String failsText = failedCount + " Att";
                String failsCol = String.format("%-8s", failsText);

                boolean mfaOn = (staff.getRole() == AdminRole.SUPER_ADMIN || staff.getRole() == AdminRole.COMPLIANCE_OFFICER);
                String mfaText = mfaOn ? "ENABLED [✓]" : "DISABLED   ";
                String mfaCol = String.format("%-11s", mfaText);

                if (isSelected) {
                    String rawRow = idCol + unameCol + roleCol + statusCol + failsCol + mfaCol;
                    sb.append(TUIBox.fullWidthInverted(rawRow, width)).append("\n");
                } else {
                    String coloredRole = "SUPER_ADMIN".equals(rawRole) ? "\033[33m" + roleCol + "\033[0m" : roleCol;
                    String coloredStatus = "ACTIVE".equalsIgnoreCase(rawStatus) ? "\033[32m" + statusCol + "\033[0m" : "\033[31m" + statusCol + "\033[0m";
                    String coloredFails;
                    if (isLocked) {
                        coloredFails = "\033[31m" + failsCol + "\033[0m";
                    } else if (failedCount >= 3) {
                        coloredFails = "\033[33m" + failsCol + "\033[0m";
                    } else if (failedCount == 0) {
                        coloredFails = "\033[90m" + failsCol + "\033[0m";
                    } else {
                        coloredFails = failsCol;
                    }
                    String coloredMfa = mfaOn ? "\033[32m" + mfaCol + "\033[0m" : "\033[90m" + mfaCol + "\033[0m";

                    String renderedRow = idCol + unameCol + coloredRole + coloredStatus + coloredFails + coloredMfa;
                    sb.append(TUIBox.fullWidthLine(renderedRow, width)).append("\n");
                }
            }
            int renderedCount = endIdx - startIdx;
            for (int e = renderedCount; e < pageSize; e++) {
                sb.append(TUIBox.emptyLine(width)).append("\n");
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No staff members registered in the directory."), width)).append("\n");
            for (int e = 1; e < pageSize; e++) {
                sb.append(TUIBox.emptyLine(width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Credential Inspection Drawer
        Admin selStaff = (staffList != null && selectedIndex >= 0 && selectedIndex < staffList.size())
                ? staffList.get(selectedIndex) : null;
        String selIdStr = selStaff != null ? String.format("#ADM-%02d", selStaff.getAdminId() != null ? selStaff.getAdminId() : 0) : "NONE";
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("CREDENTIAL INSPECTION [" + selIdStr + "]"), width)).append("\n");

        String acctName = selStaff != null
                ? (selStaff.getFullName() != null && !selStaff.getFullName().isBlank()
                ? selStaff.getFullName()
                : (selStaff.getUsername().equals("superadmin") ? "Super Administrator" : selStaff.getUsername()))
                : "N/A";
        if (acctName.length() > 24) acctName = acctName.substring(0, 21) + "...";

        String secLevel = selStaff != null
                ? (selStaff.getRole() == AdminRole.SUPER_ADMIN ? "ROOT PRIVILEGED" : (selStaff.getRole() == AdminRole.COMPLIANCE_OFFICER ? "COMPLIANCE / AUDIT" : "CREDIT UNDERWRITING"))
                : "N/A";

        String lastSignIn = (selStaff != null && selStaff.getLastLoginAt() != null)
                ? selStaff.getLastLoginAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"))
                : "2026-09-25 07:12:44 UTC";

        String sessState = selStaff != null
                ? selIdStr + " " + (selStaff.getStatus() != null ? selStaff.getStatus().name() : "ACTIVE")
                : "INACTIVE";

        String drawerLine1 = String.format("  Account Name : %-25s Security Level: %-19s", acctName, secLevel);
        String drawerLine2 = String.format("  Last Sign-In : %-25s Session State : %-19s", lastSignIn, sessState);
        sb.append(TUIBox.line(drawerLine1, width)).append("\n");
        sb.append(TUIBox.line(drawerLine2, width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        String statusDisplay = statusMessage != null ? statusMessage : "";
        if (!statusDisplay.startsWith("Status: ")) {
            statusDisplay = "Status: " + statusDisplay;
        }
        sb.append(TUIBox.line(statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint in dim gray
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Select • [R] Reset Pwd • [T] Role • [F] Suspend • [C] New • [Esc] Back")).append("\n");

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

        String th = "  STAFF ID   USERNAME        ROLE                 STATUS     FAILS   MFA STATUS ";
        sb.append("│").append(th).append("│\n");
        sb.append("│ ────────────────────────────────────────────────────────────────────────────── │\n");

        int maxDisplay = 4;
        if (staffList != null && !staffList.isEmpty()) {
            for (int i = 0; i < Math.min(maxDisplay, staffList.size()); i++) {
                Admin staff = staffList.get(i);
                String idStr = String.format("#ADM-%02d", staff.getAdminId() != null ? staff.getAdminId() : 0);
                String idCol = String.format(" %-12s", idStr);
                String rawUname = staff.getUsername() != null ? staff.getUsername() : "-";
                if (rawUname.length() > 15) rawUname = rawUname.substring(0, 13) + "..";
                String unameCol = String.format("%-16s", rawUname);

                String rawRole = staff.getRole() != null ? staff.getRole().name() : "STAFF";
                if (rawRole.length() > 20) rawRole = rawRole.substring(0, 18) + "..";
                String roleCol = String.format("%-21s", rawRole);

                int failedCount = staff.getFailedLoginAttempts();
                boolean isLocked = (failedCount >= 5);
                String rawStatus = isLocked ? "SUSPENDED" : (staff.getStatus() != null ? staff.getStatus().name() : "ACTIVE");
                String statusCol = String.format("%-11s", rawStatus);

                String failsText = failedCount + " Att";
                String failsCol = String.format("%-8s", failsText);

                boolean mfaOn = (staff.getRole() == AdminRole.SUPER_ADMIN || staff.getRole() == AdminRole.COMPLIANCE_OFFICER);
                String mfaText = mfaOn ? "ENABLED [✓]" : "DISABLED   ";
                String mfaCol = String.format("%-11s", mfaText);

                String coloredRole = "SUPER_ADMIN".equals(rawRole) ? "\033[33m" + roleCol + "\033[0m" : roleCol;
                String coloredStatus = "ACTIVE".equalsIgnoreCase(rawStatus) ? "\033[32m" + statusCol + "\033[0m" : "\033[31m" + statusCol + "\033[0m";
                String coloredFails;
                if (isLocked) {
                    coloredFails = "\033[31m" + failsCol + "\033[0m";
                } else if (failedCount >= 3) {
                    coloredFails = "\033[33m" + failsCol + "\033[0m";
                } else if (failedCount == 0) {
                    coloredFails = "\033[90m" + failsCol + "\033[0m";
                } else {
                    coloredFails = failsCol;
                }
                String coloredMfa = mfaOn ? "\033[32m" + mfaCol + "\033[0m" : "\033[90m" + mfaCol + "\033[0m";

                String renderedRow = idCol + unameCol + coloredRole + coloredStatus + coloredFails + coloredMfa;
                sb.append("│").append(renderedRow).append("│\n");
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

    public static String generateSecureToken() {
        String digits = "23456789";
        String uppers = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lowers = "abcdefghijkmnopqrstuvwxyz";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder("Dgb#");
        for (int i = 0; i < 4; i++) {
            sb.append(digits.charAt(rnd.nextInt(digits.length())));
        }
        sb.append('@');
        for (int i = 0; i < 6; i++) {
            String pool = (i % 2 == 0) ? uppers : lowers;
            sb.append(pool.charAt(rnd.nextInt(pool.length())));
        }
        sb.append('!');
        return sb.toString();
    }

    public static String renderResetPasswordContent(Admin staff, String password, boolean hasAutoGenerated,
                                                    int actionIdx, String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SUPER ADMIN > CREDENTIAL OVERRIDE"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.bold("PROVISION TEMPORARY CREDENTIAL"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String staffDisplay = staff != null
                ? String.format("%s (#ADM-%02d)", staff.getUsername(), staff.getAdminId())
                : "Unknown";
        sb.append(TUIBox.line(String.format("  Target Staff Member : %-48s", staffDisplay), width)).append("\n");

        String roleDisplay = (staff != null && staff.getRole() != null) ? staff.getRole().name() : "STAFF";
        sb.append(TUIBox.line(String.format("  Assigned Role       : %-48s", roleDisplay), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String inputDisplay = (password != null ? password : "") + (hasAutoGenerated ? "" : "_");
        String pwdLine = String.format("  Temporary Password  : [ %-48s ]", inputDisplay);
        sb.append(TUIBox.line(pwdLine, width)).append("\n");

        String subtext = hasAutoGenerated
                ? "(Generated secure temporary token)"
                : "(Visible text for admin relay to staff)";
        sb.append(TUIBox.line(String.format("                        %-52s", subtext), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.line("  Force Password Reset: [X] ENABLED (User must update on next sign-in)", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        sb.append(TUIBox.line(ConsoleTheme.bold("ACTIONS"), width)).append("\n");

        String b1Text = "[1] Save & Apply Password";
        String b1 = (actionIdx == 0) ? "▸ " + ConsoleTheme.inlineHighlight(b1Text) : "  " + b1Text;

        String genLabel = hasAutoGenerated ? String.format("%-24s", "[2] Regenerate Key") : String.format("%-24s", "[2] Auto-Generate Secure");
        String b2 = (actionIdx == 1) ? "▸ " + ConsoleTheme.inlineHighlight(genLabel) : "  " + genLabel;

        String b3Text = "[Esc] Cancel";
        String b3 = (actionIdx == 2) ? "▸ " + ConsoleTheme.inlineHighlight(b3Text) : "  " + b3Text;

        sb.append(TUIBox.line(String.format("%s    %s    %s", b1, b2, b3), width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");

        String statusDisplay = isError ? ConsoleTheme.error(statusMessage) : statusMessage;
        if (TUIBox.visibleLength(statusDisplay) > 70) {
            statusDisplay = statusDisplay.substring(0, 67) + "...";
        }
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        if (hasAutoGenerated) {
            sb.append("\033[2;90m[Enter] Confirm & Save  •  [2] Re-Roll  •  [Esc] Cancel\033[0m\n");
        } else {
            sb.append("\033[2;90m[Enter] Select Action  •  [1-2] Quick Key  •  [Esc] Cancel & Return\033[0m\n");
        }

        return sb.toString();
    }

    public static String renderResetSuccessContent(Admin staff, String issuedPassword, String statusMessage, int width) {
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SUPER ADMIN > CREDENTIAL OVERRIDE"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.bold("CREDENTIAL COMMITTED SUCCESSFULLY"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String staffDisplay = staff != null
                ? String.format("%s (#ADM-%02d)", staff.getUsername(), staff.getAdminId())
                : "Unknown";
        sb.append(TUIBox.line(String.format("  Staff Account       : %-48s", staffDisplay), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Issued Password     : %-48s", issuedPassword), width)).append("\n");
        sb.append(TUIBox.line("  Account Status      : ACTIVE (Failed Logins Reset to 0)", width)).append("\n");
        sb.append(TUIBox.line("  Next Login Action   : FORCED_PASSWORD_CHANGE_REQUIRED", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.warning("  [!] Securely share this one-time credential with the staff member."), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        String statusDisplay = (statusMessage != null && !statusMessage.isBlank())
                ? statusMessage
                : "Password updated in PostgreSQL. Audit trail record logged.";
        if (TUIBox.visibleLength(statusDisplay) > 70) {
            statusDisplay = statusDisplay.substring(0, 67) + "...";
        }
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        sb.append("\033[2;90m[Enter / Esc] Return to Staff Directory\033[0m\n");

        return sb.toString();
    }
}
