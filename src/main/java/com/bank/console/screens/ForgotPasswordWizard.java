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
import com.bank.controller.AuthController;
import com.bank.model.entity.User;
import com.bank.security.PasswordValidator;
import com.bank.service.AuthService;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 3-Step Interactive Account Password Recovery Wizard (82 Columns).
 * Step 1: Identify Account
 * Step 2: Verify OTP
 * Step 3: Reset Credentials
 */
public class ForgotPasswordWizard implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordWizard.class);

    private final AuthController authController;
    private final AuthService authService;

    private enum WizardStep {
        IDENTIFY_ACCOUNT,
        VERIFY_OTP,
        RESET_CREDENTIALS
    }

    private final String initialIdentifier;

    public ForgotPasswordWizard() {
        this(ControllerFactory.getAuthController(), "");
    }

    public ForgotPasswordWizard(String initialIdentifier) {
        this(ControllerFactory.getAuthController(), initialIdentifier);
    }

    public ForgotPasswordWizard(AuthController authController) {
        this(authController, "");
    }

    public ForgotPasswordWizard(AuthController authController, String initialIdentifier) {
        this.authController = authController;
        this.authService = ControllerFactory.getAuthService();
        this.initialIdentifier = initialIdentifier != null ? initialIdentifier : "";
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        WizardStep currentStep = WizardStep.IDENTIFY_ACCOUNT;

        // Step 1 State
        StringBuilder identifierBuf = new StringBuilder(initialIdentifier);
        int step1ActionIdx = 0; // 0: Dispatch Recovery OTP, 1: Cancel & Return

        // Step 2 State
        User recoveryUser = null;
        String dispatchedOtp = "";
        LocalDateTime otpExpiresAt = null;
        String maskedEmail = "";
        StringBuilder otpInputBuf = new StringBuilder();
        int step2ActionIdx = 0; // 0: Verify & Proceed, 1: Resend OTP, 2: Abort Recovery
        LocalDateTime lastDispatchTime = LocalDateTime.now();

        // Step 3 State
        StringBuilder newPasswordBuf = new StringBuilder();
        StringBuilder confirmPasswordBuf = new StringBuilder();
        int step3FocusedField = 0; // 0: newPassword, 1: confirmPassword, 2: actions
        int step3ActionIdx = 0; // 0: Commit, 1: Discard

        String statusMessage = "Enter username/email and select [1] to send authentication code.";
        boolean isErrorStatus = false;
        boolean firstRender = true;

        try {
            while (true) {
                StringBuilder sb = new StringBuilder();

                if (currentStep == WizardStep.IDENTIFY_ACCOUNT) {
                    renderStep1(sb, width, identifierBuf.toString(), step1ActionIdx, statusMessage, isErrorStatus);
                } else if (currentStep == WizardStep.VERIFY_OTP) {
                    int remainingAttempts = authService.getRemainingOtpAttempts(recoveryUser.getUserId());
                    renderStep2(sb, width, maskedEmail, dispatchedOtp, otpExpiresAt, otpInputBuf.toString(),
                            remainingAttempts, step2ActionIdx, statusMessage, isErrorStatus);
                } else if (currentStep == WizardStep.RESET_CREDENTIALS) {
                    renderStep3(sb, width, recoveryUser, newPasswordBuf.toString(), confirmPasswordBuf.toString(),
                            step3FocusedField, step3ActionIdx, statusMessage, isErrorStatus);
                }

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);

                // ESC Global Cancel
                if (event.action() == KeyAction.ESCAPE) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }

                // ==========================================
                // STEP 1: IDENTIFY ACCOUNT
                // ==========================================
                if (currentStep == WizardStep.IDENTIFY_ACCOUNT) {
                    if (event.action() == KeyAction.LEFT || event.action() == KeyAction.SHIFT_TAB) {
                        step1ActionIdx = (step1ActionIdx - 1 + 2) % 2;
                    } else if (event.action() == KeyAction.RIGHT || event.action() == KeyAction.TAB) {
                        step1ActionIdx = (step1ActionIdx + 1) % 2;
                    } else if (event.action() == KeyAction.BACKSPACE) {
                        if (identifierBuf.length() > 0) {
                            identifierBuf.deleteCharAt(identifierBuf.length() - 1);
                        }
                    } else if (event.action() == KeyAction.CHAR || event.action() == KeyAction.DIGIT) {
                        if (identifierBuf.length() < 48) {
                            identifierBuf.append(event.ch());
                        }
                    } else if (event.action() == KeyAction.ENTER) {
                        if (step1ActionIdx == 1) {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                        // Dispatch Recovery OTP
                        String input = identifierBuf.toString().trim();
                        if (input.isEmpty()) {
                            statusMessage = "Account identifier cannot be empty.";
                            isErrorStatus = true;
                            continue;
                        }
                        try {
                            AuthService.PasswordResetInitiationResult res = authService.initiatePasswordReset(input);
                            recoveryUser = res.user();
                            dispatchedOtp = res.otpCode();
                            otpExpiresAt = res.expiresAt();
                            maskedEmail = res.maskedEmail();
                            lastDispatchTime = LocalDateTime.now();
                            currentStep = WizardStep.VERIFY_OTP;
                            otpInputBuf.setLength(0);
                            statusMessage = "OTP dispatched successfully. Enter code to verify.";
                            isErrorStatus = false;
                        } catch (Exception e) {
                            statusMessage = "No account registered with provided credentials.";
                            isErrorStatus = true;
                        }
                    } else if (event.action() == KeyAction.DIGIT && event.ch() == '1') {
                        step1ActionIdx = 0;
                        String input = identifierBuf.toString().trim();
                        if (input.isEmpty()) {
                            statusMessage = "Account identifier cannot be empty.";
                            isErrorStatus = true;
                            continue;
                        }
                        try {
                            AuthService.PasswordResetInitiationResult res = authService.initiatePasswordReset(input);
                            recoveryUser = res.user();
                            dispatchedOtp = res.otpCode();
                            otpExpiresAt = res.expiresAt();
                            maskedEmail = res.maskedEmail();
                            lastDispatchTime = LocalDateTime.now();
                            currentStep = WizardStep.VERIFY_OTP;
                            otpInputBuf.setLength(0);
                            statusMessage = "OTP dispatched successfully. Enter code to verify.";
                            isErrorStatus = false;
                        } catch (Exception e) {
                            statusMessage = "No account registered with provided credentials.";
                            isErrorStatus = true;
                        }
                    } else if (event.action() == KeyAction.DIGIT && event.ch() == '2') {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                }

                // ==========================================
                // STEP 2: VERIFY OTP
                // ==========================================
                else if (currentStep == WizardStep.VERIFY_OTP) {
                    if (event.action() == KeyAction.LEFT || event.action() == KeyAction.SHIFT_TAB) {
                        step2ActionIdx = (step2ActionIdx - 1 + 3) % 3;
                    } else if (event.action() == KeyAction.RIGHT || event.action() == KeyAction.TAB) {
                        step2ActionIdx = (step2ActionIdx + 1) % 3;
                    } else if (event.action() == KeyAction.BACKSPACE) {
                        if (otpInputBuf.length() > 0) {
                            otpInputBuf.deleteCharAt(otpInputBuf.length() - 1);
                        }
                    } else if (event.action() == KeyAction.DIGIT || (event.action() == KeyAction.CHAR && Character.isDigit(event.ch()))) {
                        if (otpInputBuf.length() < 6) {
                            otpInputBuf.append(event.ch());
                        }
                    } else if (event.action() == KeyAction.ENTER) {
                        if (step2ActionIdx == 2) {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        } else if (step2ActionIdx == 1) {
                            // Resend OTP
                            long secondsSince = Duration.between(lastDispatchTime, LocalDateTime.now()).getSeconds();
                            if (secondsSince < 60) {
                                statusMessage = "Please wait " + (60 - secondsSince) + "s before requesting a new OTP.";
                                isErrorStatus = true;
                                continue;
                            }
                            try {
                                AuthService.PasswordResetInitiationResult res = authService.initiatePasswordReset(recoveryUser.getUsername());
                                dispatchedOtp = res.otpCode();
                                otpExpiresAt = res.expiresAt();
                                lastDispatchTime = LocalDateTime.now();
                                otpInputBuf.setLength(0);
                                statusMessage = "New OTP dispatched successfully.";
                                isErrorStatus = false;
                            } catch (Exception e) {
                                statusMessage = "Failed to resend OTP: " + e.getMessage();
                                isErrorStatus = true;
                            }
                        } else {
                            // Verify & Proceed
                            String enteredCode = otpInputBuf.toString().trim();
                            if (enteredCode.length() != 6) {
                                statusMessage = "Please enter the complete 6-digit OTP code.";
                                isErrorStatus = true;
                                continue;
                            }
                            boolean valid = authService.verifyOtp(recoveryUser.getUserId(), enteredCode);
                            if (valid) {
                                currentStep = WizardStep.RESET_CREDENTIALS;
                                statusMessage = "OTP matched successfully. Press [1] to advance to password reset.";
                                isErrorStatus = false;
                            } else {
                                int remaining = authService.getRemainingOtpAttempts(recoveryUser.getUserId());
                                if (remaining <= 0) {
                                    statusMessage = "Maximum recovery attempts exceeded. Recovery locked.";
                                    isErrorStatus = true;
                                    terminal.setAttributes(origAttributes);
                                    navigator.pop();
                                    return;
                                } else {
                                    statusMessage = "Invalid OTP code. Attempts remaining: " + remaining + " / 3.";
                                    isErrorStatus = true;
                                }
                            }
                        }
                    } else if (event.action() == KeyAction.CHAR && event.ch() == '3') {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    } else if (event.action() == KeyAction.CHAR && event.ch() == '2' && otpInputBuf.length() == 0) {
                        step2ActionIdx = 1;
                        long secondsSince = Duration.between(lastDispatchTime, LocalDateTime.now()).getSeconds();
                        if (secondsSince < 60) {
                            statusMessage = "Please wait " + (60 - secondsSince) + "s before requesting a new OTP.";
                            isErrorStatus = true;
                        } else {
                            try {
                                AuthService.PasswordResetInitiationResult res = authService.initiatePasswordReset(recoveryUser.getUsername());
                                dispatchedOtp = res.otpCode();
                                otpExpiresAt = res.expiresAt();
                                lastDispatchTime = LocalDateTime.now();
                                statusMessage = "New OTP dispatched successfully.";
                                isErrorStatus = false;
                            } catch (Exception e) {
                                statusMessage = "Failed to resend OTP: " + e.getMessage();
                                isErrorStatus = true;
                            }
                        }
                    } else if (event.action() == KeyAction.CHAR && event.ch() == '1' && otpInputBuf.length() == 6) {
                        step2ActionIdx = 0;
                        String enteredCode = otpInputBuf.toString().trim();
                        boolean valid = authService.verifyOtp(recoveryUser.getUserId(), enteredCode);
                        if (valid) {
                            currentStep = WizardStep.RESET_CREDENTIALS;
                            statusMessage = "OTP matched successfully. Press [1] to advance to password reset.";
                            isErrorStatus = false;
                        } else {
                            int remaining = authService.getRemainingOtpAttempts(recoveryUser.getUserId());
                            statusMessage = "Invalid OTP code. Attempts remaining: " + remaining + " / 3.";
                            isErrorStatus = true;
                        }
                    }
                }

                // ==========================================
                // STEP 3: RESET CREDENTIALS
                // ==========================================
                else if (currentStep == WizardStep.RESET_CREDENTIALS) {
                    if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                        step3FocusedField = (step3FocusedField + 1) % 3;
                    } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                        step3FocusedField = (step3FocusedField - 1 + 3) % 3;
                    } else if (event.action() == KeyAction.LEFT && step3FocusedField == 2) {
                        step3ActionIdx = (step3ActionIdx - 1 + 2) % 2;
                    } else if (event.action() == KeyAction.RIGHT && step3FocusedField == 2) {
                        step3ActionIdx = (step3ActionIdx + 1) % 2;
                    } else if (event.action() == KeyAction.BACKSPACE) {
                        if (step3FocusedField == 0 && newPasswordBuf.length() > 0) {
                            newPasswordBuf.deleteCharAt(newPasswordBuf.length() - 1);
                        } else if (step3FocusedField == 1 && confirmPasswordBuf.length() > 0) {
                            confirmPasswordBuf.deleteCharAt(confirmPasswordBuf.length() - 1);
                        }
                    } else if (event.action() == KeyAction.CHAR || event.action() == KeyAction.DIGIT) {
                        if (step3FocusedField == 0 && newPasswordBuf.length() < 32) {
                            newPasswordBuf.append(event.ch());
                        } else if (step3FocusedField == 1 && confirmPasswordBuf.length() < 32) {
                            confirmPasswordBuf.append(event.ch());
                        }
                    } else if (event.action() == KeyAction.ENTER) {
                        if (step3FocusedField < 2) {
                            step3FocusedField++;
                            continue;
                        }
                        if (step3ActionIdx == 1) {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }

                        // Commit Password Change
                        String p1 = newPasswordBuf.toString();
                        String p2 = confirmPasswordBuf.toString();

                        if (p1.isEmpty() || p2.isEmpty()) {
                            statusMessage = "Please enter and confirm your new password.";
                            isErrorStatus = true;
                            continue;
                        }
                        if (!p1.equals(p2)) {
                            statusMessage = "Passwords do not match.";
                            isErrorStatus = true;
                            continue;
                        }

                        var eval = PasswordValidator.evaluate(p1);
                        if (!eval.isValid()) {
                            statusMessage = "Password does not meet required complexity standards.";
                            isErrorStatus = true;
                            continue;
                        }

                        try {
                            authService.resetPasswordWithOtp(recoveryUser.getUserId(), dispatchedOtp, p1, p2);
                            terminal.setAttributes(origAttributes);
                            navigator.clearAndPush(new LoginScreen());
                            return;
                        } catch (Exception e) {
                            statusMessage = "Reset failed: " + e.getMessage();
                            isErrorStatus = true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in ForgotPasswordWizard", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void renderStep1(StringBuilder sb, int width, String identifier, int actionIdx, String statusMsg, boolean isError) {
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > ACCOUNT RECOVERY > IDENTIFY ACCOUNT"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("RECOVERY INITIATION", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.line("  Enter your registered username or primary email address to receive an", width)).append("\n");
        sb.append(TUIBox.line("  authentication one-time password (OTP).", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String displayVal = identifier + "_";
        String padded = String.format("%-46s", displayVal.length() > 46 ? displayVal.substring(0, 46) : displayVal);
        sb.append(TUIBox.line("  Account Identifier   : [ " + ConsoleTheme.inlineHighlight(padded) + " ]", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("SECURITY NOTICE", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.line("  • An OTP will be dispatched to the verified communication channel on record.", width)).append("\n");
        sb.append(TUIBox.line("  • Account access will be temporarily locked after 5 failed recovery queries.", width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("ACTION", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String b0 = actionIdx == 0 ? "▸ " + ConsoleTheme.highlight("[1] Dispatch Recovery OTP") : "  [1] Dispatch Recovery OTP";
        String b1 = actionIdx == 1 ? "▸ " + ConsoleTheme.highlight("[2] Cancel & Return") : "  [2] Cancel & Return";
        sb.append(TUIBox.line("  " + b0 + "                     " + b1, width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        if (statusMsg != null) {
            String styledStatus = isError ? ConsoleTheme.error(statusMsg) : ConsoleTheme.success(statusMsg);
            sb.append(TUIBox.line("Status: " + styledStatus, width)).append("\n");
        }
        sb.append(TUIBox.bottom(width)).append("\n");
        sb.append(ConsoleTheme.keyGuide("[Enter] Submit Identifier  •  [1/2] Quick Action  •  [Esc] Back to Welcome Menu")).append("\n");
    }

    private void renderStep2(StringBuilder sb, int width, String maskedEmail, String otpCode, LocalDateTime expiresAt,
                            String enteredOtp, int remainingAttempts, int actionIdx, String statusMsg, boolean isError) {
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > ACCOUNT RECOVERY > VERIFY OTP"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("SECURITY CHALLENGE", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.line("  Destination Target   : " + maskedEmail + " (Masked for Privacy)", width)).append("\n");
        sb.append(TUIBox.line("  Dispatch Status      : " + ConsoleTheme.success("SIMULATED") + " (Dev Mode: OTP is [ " + ConsoleTheme.highlight(otpCode) + " ])", width)).append("\n");

        long remainingSec = expiresAt != null ? Math.max(0, Duration.between(LocalDateTime.now(), expiresAt).getSeconds()) : 180;
        String ttlStr = String.format("%02d:%02d Remaining", remainingSec / 60, remainingSec % 60);
        sb.append(TUIBox.line("  Time-To-Live (TTL)   : " + ttlStr, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String displayVal = enteredOtp + "_";
        String padded = String.format("%-46s", displayVal.length() > 46 ? displayVal.substring(0, 46) : displayVal);
        sb.append(TUIBox.line("  Enter 6-Digit OTP    : [ " + ConsoleTheme.inlineHighlight(padded) + " ]", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("ATTEMPTS REMAINING: " + remainingAttempts + " / 3", width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("ACTION", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String b0 = actionIdx == 0 ? "▸ " + ConsoleTheme.highlight("[1] Verify & Proceed") : "  [1] Verify & Proceed";
        String b1 = actionIdx == 1 ? "▸ " + ConsoleTheme.highlight("[2] Resend OTP (Wait 60s)") : "  [2] Resend OTP (Wait 60s)";
        String b2 = actionIdx == 2 ? "▸ " + ConsoleTheme.highlight("[3] Abort Recovery") : "  [3] Abort Recovery";
        sb.append(TUIBox.line("  " + b0 + "    " + b1 + "    " + b2, width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        if (statusMsg != null) {
            String styledStatus = isError ? ConsoleTheme.error(statusMsg) : ConsoleTheme.success(statusMsg);
            sb.append(TUIBox.line("Status: " + styledStatus, width)).append("\n");
        }
        sb.append(TUIBox.bottom(width)).append("\n");
        sb.append(ConsoleTheme.keyGuide("[Enter] Verify OTP  •  [1-3] Quick Action  •  [Esc] Cancel")).append("\n");
    }

    private void renderStep3(StringBuilder sb, int width, User user, String newPassword, String confirmPassword,
                            int focusedField, int actionIdx, String statusMsg, boolean isError) {
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > ACCOUNT RECOVERY > RESET CREDENTIALS"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("SET NEW SECURE PASSWORD", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String userNameDisplay = user != null ? (user.getFullName() != null ? user.getFullName().toUpperCase() : user.getUsername()) + " (#USR-" + user.getUserId() + ")" : "USER (#USR)";
        sb.append(TUIBox.line("  Target Account       : " + userNameDisplay, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String mask1 = "•".repeat(newPassword.length()) + (focusedField == 0 ? "_" : "");
        String mask2 = "•".repeat(confirmPassword.length()) + (focusedField == 1 ? "_" : "");
        String padded1 = String.format("%-46s", mask1.length() > 46 ? mask1.substring(0, 46) : mask1);
        String padded2 = String.format("%-46s", mask2.length() > 46 ? mask2.substring(0, 46) : mask2);

        String field1 = focusedField == 0 ? ConsoleTheme.inlineHighlight(padded1) : padded1;
        String field2 = focusedField == 1 ? ConsoleTheme.inlineHighlight(padded2) : padded2;

        sb.append(TUIBox.line("  New Password         : [ " + field1 + " ]", width)).append("\n");
        sb.append(TUIBox.line("  Confirm Password     : [ " + field2 + " ]", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("PASSWORD COMPLEXITY CHECK", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        var eval = PasswordValidator.evaluate(newPassword);
        String c1 = eval.lengthMet() ? ConsoleTheme.success("✔") : " ";
        String c2 = eval.upperLowerMet() ? ConsoleTheme.success("✔") : " ";
        String c3 = eval.numberMet() ? ConsoleTheme.success("✔") : " ";
        String c4 = eval.symbolMet() ? ConsoleTheme.success("✔") : " ";

        String checkRow1 = String.format("  [%s] Min 8 Characters        [%s] Uppercase & Lowercase Letter", c1, c2);
        String checkRow2 = String.format("  [%s] Numeric Digit (0-9)     [%s] Special Symbol (@, #, $, %%, etc.)", c3, c4);
        sb.append(TUIBox.line(checkRow1, width)).append("\n");
        sb.append(TUIBox.line(checkRow2, width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("ACTION", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String b0 = (focusedField == 2 && actionIdx == 0) ? "▸ " + ConsoleTheme.highlight("[1] Commit Password Change") : "  [1] Commit Password Change";
        String b1 = (focusedField == 2 && actionIdx == 1) ? "▸ " + ConsoleTheme.highlight("[2] Discard & Exit") : "  [2] Discard & Exit";
        sb.append(TUIBox.line("  " + b0 + "                   " + b1, width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        if (statusMsg != null) {
            String styledStatus = isError ? ConsoleTheme.error(statusMsg) : ConsoleTheme.success(statusMsg);
            sb.append(TUIBox.line("Status: " + styledStatus, width)).append("\n");
        }
        sb.append(TUIBox.bottom(width)).append("\n");
        sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Confirm  •  [1/2] Quick Action  •  [Esc] Abort")).append("\n");
    }
}
