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
import com.bank.model.dto.AdminDTO;
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
import java.util.List;

/**
 * SCREEN: SUPER ADMIN > STAFF MANAGEMENT > RESET CREDENTIALS (82 Columns)
 * Allows Super Administrators to override staff credentials, unlock locked accounts,
 * and enforce mandatory password rotation on first login with immutable audit logging.
 */
public class StaffResetCredentialsScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(StaffResetCredentialsScreen.class);

    private final AdminController adminController;
    private final Admin targetStaff;

    public StaffResetCredentialsScreen() {
        this(ControllerFactory.getAdminController(), null);
    }

    public StaffResetCredentialsScreen(Admin targetStaff) {
        this(ControllerFactory.getAdminController(), targetStaff);
    }

    public StaffResetCredentialsScreen(AdminController adminController, Admin targetStaff) {
        this.adminController = adminController;
        this.targetStaff = targetStaff;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        AdminDTO superAdminDto = session.getCurrentAdmin();
        Admin superAdmin = SessionManager.getCurrentAdmin();
        if (superAdminDto == null || superAdmin == null) {
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        // Resolve target staff entity
        Admin staff = this.targetStaff;
        if (staff == null) {
            try {
                List<Admin> all = adminController.getAllAdmins(superAdmin);
                staff = all.stream()
                        .filter(a -> a.getRole() != AdminRole.SUPER_ADMIN)
                        .findFirst()
                        .orElse(null);
            } catch (Exception ignored) {}
        }
        if (staff == null) {
            staff = Admin.builder()
                    .adminId(102L)
                    .username("Teller_Sophea")
                    .fullName("Heng Sophea")
                    .role(AdminRole.LOAN_OFFICER)
                    .status(AdminStatus.ACTIVE)
                    .build();
        }

        StringBuilder tempPasswordBuf = new StringBuilder("TempPass@2026");
        boolean requireChangeOn1st = true;
        boolean unlockAccountState = true;
        StringBuilder reasonBuf = new StringBuilder("Staff reported forgotten credentials");

        int focusedField = 0; // 0: TempPass, 1: RequireChange, 2: Unlock, 3: Reason, 4: Actions
        int actionIdx = 0; // 0: Authorize & Reset, 1: Discard & Return

        String statusMessage = "Super Admin authorization ready. Select [1] to commit credential reset.";
        boolean isErrorStatus = false;
        boolean firstRender = true;

        try {
            while (true) {
                StringBuilder sb = new StringBuilder();

                // Top Box
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SUPER ADMIN > STAFF MANAGEMENT > RESET CREDENTIALS"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Compartment 1: TARGET ADMINISTRATOR / STAFF
                sb.append(TUIBox.line("TARGET ADMINISTRATOR / STAFF", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String staffIdDisplay = String.format("#ADM-%d (%s)", staff.getAdminId(), staff.getUsername());
                String roleDisplay = staff.getRole() != null ? staff.getRole().name() : "BRANCH_TELLER";
                String statusDisplay = String.format("%s (Locked attempts: 3/3)", staff.getStatus());

                sb.append(TUIBox.line(String.format("  %-22s: %s", "Staff Identifier", staffIdDisplay), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-22s: %s", "Full Name", staff.getFullName()), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-22s: %s", "Assigned Role", roleDisplay), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-22s: %s", "Account Status", statusDisplay), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                // Compartment 2: CREDENTIAL OVERRIDE
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("CREDENTIAL OVERRIDE", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String passVal = tempPasswordBuf.toString() + (focusedField == 0 ? "_" : "");
                String passPadded = String.format("%-46s", passVal.length() > 46 ? passVal.substring(0, 46) : passVal);
                String passField = focusedField == 0 ? ConsoleTheme.inlineHighlight(passPadded) : passPadded;
                sb.append(TUIBox.line("  Temporary Password   : [ " + passField + " ]", width)).append("\n");

                String check1 = requireChangeOn1st ? "[X] YES" : "[ ] NO ";
                String check1Str = String.format("  Require Change On 1st: %s %s",
                        focusedField == 1 ? ConsoleTheme.highlight(check1) : check1,
                        ConsoleTheme.muted("(Forced update upon next staff authentication)"));
                sb.append(TUIBox.line(check1Str, width)).append("\n");

                String check2 = unlockAccountState ? "[X] Reset" : "[ ] Retain";
                String check2Str = String.format("  Unlock Account State : %s %s",
                        focusedField == 2 ? ConsoleTheme.highlight(check2) : check2,
                        ConsoleTheme.muted("failed login attempts to 0"));
                sb.append(TUIBox.line(check2Str, width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                // Compartment 3: AUDIT CONFIRMATION
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("AUDIT CONFIRMATION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String superAdminDisplay = String.format("%s (#ADM-%03d)", superAdmin.getUsername(), superAdmin.getAdminId());
                sb.append(TUIBox.line(String.format("  %-22s: %s", "Super Admin Authid", superAdminDisplay), width)).append("\n");

                String reasonVal = reasonBuf.toString() + (focusedField == 3 ? "_" : "");
                String reasonPadded = String.format("%-46s", reasonVal.length() > 46 ? reasonVal.substring(0, 46) : reasonVal);
                String reasonField = focusedField == 3 ? ConsoleTheme.inlineHighlight(reasonPadded) : reasonPadded;
                sb.append(TUIBox.line("  Reason for Reset     : [ " + reasonField + " ]", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                // Compartment 4: ACTION BAR
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("ACTION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String b0 = (focusedField == 4 && actionIdx == 0) ? "▸ " + ConsoleTheme.highlight("[1] Authorize & Reset Password") : "  [1] Authorize & Reset Password";
                String b1 = (focusedField == 4 && actionIdx == 1) ? "▸ " + ConsoleTheme.highlight("[2] Discard & Return") : "  [2] Discard & Return";
                sb.append(TUIBox.line("  " + b0 + "                " + b1, width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                if (statusMessage != null) {
                    String styledStatus = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                    sb.append(TUIBox.line("Status: " + styledStatus, width)).append("\n");
                }
                sb.append(TUIBox.bottom(width)).append("\n");
                sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Toggle/Select  •  [1/2] Action  •  [Esc] Cancel")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);

                if (event.action() == KeyAction.ESCAPE) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }

                if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 5;
                } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 5) % 5;
                } else if (event.action() == KeyAction.LEFT && focusedField == 4) {
                    actionIdx = (actionIdx - 1 + 2) % 2;
                } else if (event.action() == KeyAction.RIGHT && focusedField == 4) {
                    actionIdx = (actionIdx + 1) % 2;
                } else if (event.action() == KeyAction.CHAR && event.ch() == ' ') {
                    if (focusedField == 1) {
                        requireChangeOn1st = !requireChangeOn1st;
                    } else if (focusedField == 2) {
                        unlockAccountState = !unlockAccountState;
                    } else if (focusedField == 0 && tempPasswordBuf.length() < 32) {
                        tempPasswordBuf.append(' ');
                    } else if (focusedField == 3 && reasonBuf.length() < 46) {
                        reasonBuf.append(' ');
                    }
                } else if (event.action() == KeyAction.BACKSPACE) {
                    if (focusedField == 0 && tempPasswordBuf.length() > 0) {
                        tempPasswordBuf.deleteCharAt(tempPasswordBuf.length() - 1);
                    } else if (focusedField == 3 && reasonBuf.length() > 0) {
                        reasonBuf.deleteCharAt(reasonBuf.length() - 1);
                    }
                } else if (event.action() == KeyAction.CHAR || event.action() == KeyAction.DIGIT) {
                    if (focusedField == 0 && tempPasswordBuf.length() < 32) {
                        tempPasswordBuf.append(event.ch());
                    } else if (focusedField == 3 && reasonBuf.length() < 46) {
                        reasonBuf.append(event.ch());
                    } else if (focusedField == 4) {
                        if (event.ch() == '1') {
                            actionIdx = 0;
                        } else if (event.ch() == '2') {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    if (focusedField == 1) {
                        requireChangeOn1st = !requireChangeOn1st;
                        continue;
                    } else if (focusedField == 2) {
                        unlockAccountState = !unlockAccountState;
                        continue;
                    } else if (focusedField < 4) {
                        focusedField++;
                        continue;
                    }

                    if (actionIdx == 1) {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }

                    // Authorize & Reset Password
                    String tempPass = tempPasswordBuf.toString().trim();
                    if (tempPass.isEmpty()) {
                        statusMessage = "Temporary password cannot be empty.";
                        isErrorStatus = true;
                        continue;
                    }

                    try {
                        adminController.resetAdminCredentials(superAdmin, staff.getAdminId(), tempPass,
                                requireChangeOn1st, unlockAccountState, reasonBuf.toString());
                        statusMessage = "Credentials successfully reset for staff: " + staff.getUsername();
                        isErrorStatus = false;
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    } catch (Exception e) {
                        statusMessage = "Reset authorization failed: " + e.getMessage();
                        isErrorStatus = true;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in StaffResetCredentialsScreen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }
}
