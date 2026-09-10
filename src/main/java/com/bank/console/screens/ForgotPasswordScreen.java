package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AuthController;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * DEDICATED SCREEN: PASSWORD RECOVERY GATEWAY (82 Columns)
 * Supports universal OTP recovery for users and security question recovery for staff.
 */
public class ForgotPasswordScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordScreen.class);

    private final AuthController authController;
    private String statusMessage;
    private boolean isErrorStatus;

    public ForgotPasswordScreen() {
        this(ControllerFactory.getAuthController());
    }

    public ForgotPasswordScreen(AuthController authController) {
        this.authController = authController;
        this.statusMessage = "Select recovery option or enter 0 to return";
        this.isErrorStatus = false;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
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

                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > PASSWORD RECOVERY GATEWAY"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.line("  Please select your recovery method:", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String o1 = "[1] Universal OTP Recovery (Email Verification Code)";
                String o2 = "[2] Staff / Admin Security Question Recovery";
                String o3 = "[0] Return to Login / Gateway";

                sb.append(TUIBox.line(selectedIndex == 0 ? ("   ► " + ConsoleTheme.highlight(o1)) : ("     " + o1), width)).append("\n");
                sb.append(TUIBox.line(selectedIndex == 1 ? ("   ► " + ConsoleTheme.highlight(o2)) : ("     " + o2), width)).append("\n");
                sb.append(TUIBox.line(selectedIndex == 2 ? ("   ► " + ConsoleTheme.highlight(o3)) : ("     " + ConsoleTheme.muted(o3)), width)).append("\n");

                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.muted("All password reset requests are logged and monitored for fraud triage."), width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                if (statusMessage != null) {
                    String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                    sb.append(" Status: ").append(statusDisplay).append("\n");
                }
                sb.append(ConsoleTheme.muted("  [↑/↓] Navigate  •  [Enter] Select  •  [1-2] Quick Select  •  [Esc] Back")).append("\n");

                System.out.print(sb.toString());
                System.out.flush();
                firstRender = false;

                int ch = reader.read();

                if (ch == 27) { // ESC or Escape sequence
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        navigator.pop();
                        return;
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A' || code == 'D') { // Up / Left
                            selectedIndex = (selectedIndex - 1 + 3) % 3;
                        } else if (code == 'B' || code == 'C') { // Down / Right
                            selectedIndex = (selectedIndex + 1) % 3;
                        }
                    }
                } else if (ch == '\t') {
                    selectedIndex = (selectedIndex + 1) % 3;
                } else if (ch == '\r' || ch == '\n') {
                    if (selectedIndex == 0) {
                        terminal.setAttributes(origAttributes);
                        handleOtpRecovery(session);
                        origAttributes = terminal.enterRawMode();
                        firstRender = true;
                    } else if (selectedIndex == 1) {
                        terminal.setAttributes(origAttributes);
                        handleSecurityQuestionRecovery(session);
                        origAttributes = terminal.enterRawMode();
                        firstRender = true;
                    } else {
                        navigator.pop();
                        return;
                    }
                } else if (ch == '1') {
                    terminal.setAttributes(origAttributes);
                    handleOtpRecovery(session);
                    origAttributes = terminal.enterRawMode();
                    firstRender = true;
                } else if (ch == '2') {
                    terminal.setAttributes(origAttributes);
                    handleSecurityQuestionRecovery(session);
                    origAttributes = terminal.enterRawMode();
                    firstRender = true;
                } else if (ch == '0' || ch == 'b' || ch == 'B') {
                    navigator.pop();
                    return;
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            logger.error("Error in forgot password loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void handleOtpRecovery(TUISession session) {
        session.clearScreen();
        int width = TUILayout.APP_WIDTH;

        System.out.println(TUIBox.top(width));
        System.out.println(TUIBox.line(ConsoleTheme.primary("PASSWORD RECOVERY > OTP CODE VERIFICATION"), width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.line("  Step 1 of 3: Enter your registered account email.", width));
        System.out.println(TUIBox.line("  Email Address     : [                                                ]", width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.bottom(width));

        String email = ConsolePrompt.promptLine("Registered Email (or '0' to cancel)");
        if (email.isEmpty() || "0".equals(email)) {
            return;
        }

        try {
            String initMsg = authController.initiatePasswordRecovery(email);
            session.clearScreen();

            System.out.println(TUIBox.top(width));
            System.out.println(TUIBox.line(ConsoleTheme.primary("PASSWORD RECOVERY > OTP CODE VERIFICATION"), width));
            System.out.println(TUIBox.divider(width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.line(ConsoleTheme.info("  " + (initMsg != null ? initMsg : "OTP code dispatched.")), width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.line("  Step 2 of 3: Enter the 6-digit verification code sent to your email.", width));
            System.out.println(TUIBox.line("  OTP Code          : [ ###### ]", width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.bottom(width));

            String code = ConsolePrompt.promptLine("Enter 6-digit OTP code");
            if (code.isEmpty() || "0".equals(code)) {
                return;
            }

            boolean valid = authController.verifyRecoveryCode(email, code);
            if (!valid) {
                this.statusMessage = "Verification failed: Invalid or expired OTP code.";
                this.isErrorStatus = true;
                return;
            }

            session.clearScreen();
            System.out.println(TUIBox.top(width));
            System.out.println(TUIBox.line(ConsoleTheme.primary("PASSWORD RECOVERY > CREATE NEW PASSWORD"), width));
            System.out.println(TUIBox.divider(width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.line("  Step 3 of 3: Set your new secure password.", width));
            System.out.println(TUIBox.line(ConsoleTheme.muted("  Minimum 8 characters with upper, lower, digit, and symbol."), width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.bottom(width));

            String newPass = ConsolePrompt.promptPasswordRaw("New Password");
            if (newPass.isEmpty()) {
                this.statusMessage = "Password cannot be empty.";
                this.isErrorStatus = true;
                return;
            }

            String confirmPass = ConsolePrompt.promptPasswordRaw("Confirm New Password");
            if (!newPass.equals(confirmPass)) {
                this.statusMessage = "Passwords do not match.";
                this.isErrorStatus = true;
                return;
            }

            authController.resetPassword(email, code, newPass);

            session.clearScreen();
            System.out.println(TUIBox.top(width));
            System.out.println(TUIBox.line(ConsoleTheme.success("PASSWORD RECOVERY COMPLETED SUCCESSFULLY"), width));
            System.out.println(TUIBox.divider(width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.center("Your password has been securely updated.", width));
            System.out.println(TUIBox.center(ConsoleTheme.muted("You may now sign in with your new credentials."), width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.bottom(width));
            ConsolePrompt.pause();

            this.statusMessage = "Password updated successfully. Please sign in.";
            this.isErrorStatus = false;

        } catch (Exception e) {
            logger.error("Error during OTP recovery", e);
            this.statusMessage = "Recovery error: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }

    private void handleSecurityQuestionRecovery(TUISession session) {
        session.clearScreen();
        int width = TUILayout.APP_WIDTH;

        System.out.println(TUIBox.top(width));
        System.out.println(TUIBox.line(ConsoleTheme.primary("STAFF / ADMIN SECURITY QUESTION RECOVERY"), width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.line("  Staff Username    : [                                                ]", width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.bottom(width));

        String username = ConsolePrompt.promptLine("Staff Username (or '0' to cancel)");
        if (username.isEmpty() || "0".equals(username)) {
            return;
        }

        try {
            String question = authController.getSecurityQuestion(username);
            if (question == null || question.isBlank()) {
                this.statusMessage = "No security question configured for user: " + username;
                this.isErrorStatus = true;
                return;
            }

            session.clearScreen();
            System.out.println(TUIBox.top(width));
            System.out.println(TUIBox.line(ConsoleTheme.primary("STAFF / ADMIN SECURITY QUESTION RECOVERY"), width));
            System.out.println(TUIBox.divider(width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.line("  Security Question : " + ConsoleTheme.info(question), width));
            System.out.println(TUIBox.line("  Security Answer   : [                                                ]", width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.bottom(width));

            String answer = ConsolePrompt.promptLine("Security Answer");
            if (answer.isEmpty()) {
                this.statusMessage = "Security answer cannot be empty.";
                this.isErrorStatus = true;
                return;
            }

            String newPass = ConsolePrompt.promptPasswordRaw("New Admin Password");
            if (newPass.isEmpty()) {
                this.statusMessage = "Password cannot be empty.";
                this.isErrorStatus = true;
                return;
            }

            authController.recoverAdminPassword(username, answer, newPass);

            session.clearScreen();
            System.out.println(TUIBox.top(width));
            System.out.println(TUIBox.line(ConsoleTheme.success("STAFF PASSWORD RESET SUCCESSFUL"), width));
            System.out.println(TUIBox.divider(width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.center("Staff credentials updated successfully.", width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.bottom(width));
            ConsolePrompt.pause();

            this.statusMessage = "Staff password updated. Please sign in.";
            this.isErrorStatus = false;

        } catch (Exception e) {
            logger.error("Error during security question recovery", e);
            this.statusMessage = "Recovery failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }
}
