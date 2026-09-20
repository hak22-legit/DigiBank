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
import com.bank.model.dto.UserDTO;
import com.bank.model.enums.Currency;
import com.bank.security.PasswordValidator;
import com.bank.security.PasswordValidator.PasswordEvaluation;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;

/**
 * SCREEN 3: CUSTOMER REGISTRATION (82 Columns)
 * Enclosed 82-column TUI registration form with zero CLI leaks, simplified onboarding,
 * real-time enterprise password strength meter, rule checklist, and primary currency auto-provisioning.
 */
public class RegisterScreen implements Screen {
    private final AuthController authController;
    private String statusMessage;
    private boolean isErrorStatus;

    public RegisterScreen() {
        this(ControllerFactory.getAuthController());
    }

    public RegisterScreen(AuthController authController) {
        this.authController = authController;
        this.statusMessage = null;
        this.isErrorStatus = false;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        // 8 interactive fields (0..6 input, 7 action buttons)
        int focusedField = 0;
        int actionIdx = 0; // 0: Create Bank Profile, 1: Cancel & Return

        StringBuilder fullNameBuf = new StringBuilder();
        StringBuilder phoneBuf = new StringBuilder();
        StringBuilder emailBuf = new StringBuilder();
        StringBuilder usernameBuf = new StringBuilder();
        StringBuilder passwordBuf = new StringBuilder();
        StringBuilder confirmPasswordBuf = new StringBuilder();
        int currencyIdx = 0; // 0: USD, 1: KHR

        boolean firstRender = true;

        try {
            while (true) {
                renderScreen(width, focusedField, actionIdx, fullNameBuf, phoneBuf, emailBuf,
                        usernameBuf, passwordBuf, confirmPasswordBuf, currencyIdx, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);

                // Global Cancel via ESC
                if (event.action() == KeyAction.ESCAPE) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }

                // TAB / DOWN: Advance field
                if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 8;
                    statusMessage = null;
                    continue;
                }

                // SHIFT_TAB / UP: Previous field
                if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 8) % 8;
                    statusMessage = null;
                    continue;
                }

                // LEFT / RIGHT Arrow Navigation
                if (event.action() == KeyAction.LEFT) {
                    if (focusedField == 6) {
                        currencyIdx = (currencyIdx == 0) ? 1 : 0;
                    } else if (focusedField == 7) {
                        actionIdx = 0;
                    }
                    continue;
                } else if (event.action() == KeyAction.RIGHT) {
                    if (focusedField == 6) {
                        currencyIdx = (currencyIdx == 0) ? 1 : 0;
                    } else if (focusedField == 7) {
                        actionIdx = 1;
                    }
                    continue;
                }

                // SPACEBAR toggle
                if (event.action() == KeyAction.CHAR && event.ch() == ' ') {
                    if (focusedField == 6) {
                        currencyIdx = (currencyIdx == 0) ? 1 : 0;
                        continue;
                    } else if (focusedField == 0) {
                        if (fullNameBuf.length() < 45) fullNameBuf.append(' ');
                        statusMessage = null;
                        continue;
                    } else if (focusedField == 1) {
                        if (phoneBuf.length() < 20) phoneBuf.append(' ');
                        statusMessage = null;
                        continue;
                    }
                }

                // BACKSPACE handling
                if (event.action() == KeyAction.BACKSPACE) {
                    statusMessage = null;
                    switch (focusedField) {
                        case 0 -> { if (fullNameBuf.length() > 0) fullNameBuf.deleteCharAt(fullNameBuf.length() - 1); }
                        case 1 -> { if (phoneBuf.length() > 0) phoneBuf.deleteCharAt(phoneBuf.length() - 1); }
                        case 2 -> { if (emailBuf.length() > 0) emailBuf.deleteCharAt(emailBuf.length() - 1); }
                        case 3 -> { if (usernameBuf.length() > 0) usernameBuf.deleteCharAt(usernameBuf.length() - 1); }
                        case 4 -> { if (passwordBuf.length() > 0) passwordBuf.deleteCharAt(passwordBuf.length() - 1); }
                        case 5 -> { if (confirmPasswordBuf.length() > 0) confirmPasswordBuf.deleteCharAt(confirmPasswordBuf.length() - 1); }
                    }
                    continue;
                }

                // ENTER key handling
                if (event.action() == KeyAction.ENTER) {
                    if (focusedField < 6) {
                        focusedField++;
                    } else if (focusedField == 6) {
                        currencyIdx = (currencyIdx == 0) ? 1 : 0;
                    } else if (focusedField == 7) {
                        if (actionIdx == 0) {
                            boolean ok = submitRegistration(terminal, origAttributes, reader, navigator, session,
                                    fullNameBuf.toString(), phoneBuf.toString(), emailBuf.toString(),
                                    usernameBuf.toString(), passwordBuf.toString(), confirmPasswordBuf.toString(),
                                    (currencyIdx == 0 ? Currency.USD : Currency.KHR));
                            if (ok) return;
                        } else {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                    }
                    continue;
                }

                // Hotkeys '1' and '2'
                if ((event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) && (event.ch() == '1' || event.ch() == '2')) {
                    if (focusedField == 6) {
                        currencyIdx = (event.ch() == '1') ? 0 : 1;
                        continue;
                    } else if (focusedField == 7) {
                        if (event.ch() == '1') {
                            actionIdx = 0;
                            boolean ok = submitRegistration(terminal, origAttributes, reader, navigator, session,
                                    fullNameBuf.toString(), phoneBuf.toString(), emailBuf.toString(),
                                    usernameBuf.toString(), passwordBuf.toString(), confirmPasswordBuf.toString(),
                                    (currencyIdx == 0 ? Currency.USD : Currency.KHR));
                            if (ok) return;
                        } else {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                        continue;
                    }
                }

                // Typing characters and digits
                if (event.action() == KeyAction.CHAR || event.action() == KeyAction.DIGIT) {
                    char c = event.ch();
                    statusMessage = null;
                    switch (focusedField) {
                        case 0 -> {
                            if (fullNameBuf.length() < 45 && (Character.isLetter(c) || c == ' ' || c == '.' || c == '-' || c == '\'')) {
                                fullNameBuf.append(c);
                            }
                        }
                        case 1 -> {
                            if (phoneBuf.length() < 20 && (Character.isDigit(c) || c == '+' || c == '-' || c == ' ' || c == '(' || c == ')')) {
                                phoneBuf.append(c);
                            }
                        }
                        case 2 -> {
                            if (emailBuf.length() < 45 && !Character.isWhitespace(c)) {
                                emailBuf.append(c);
                            }
                        }
                        case 3 -> {
                            if (usernameBuf.length() < 30 && (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-')) {
                                usernameBuf.append(c);
                            }
                        }
                        case 4 -> {
                            if (passwordBuf.length() < 32 && c >= 32 && c <= 126) {
                                passwordBuf.append(c);
                            }
                        }
                        case 5 -> {
                            if (confirmPasswordBuf.length() < 32 && c >= 32 && c <= 126) {
                                confirmPasswordBuf.append(c);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            terminal.setAttributes(origAttributes);
            navigator.pop();
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void renderScreen(int width, int focusedField, int actionIdx,
                              StringBuilder fullNameBuf, StringBuilder phoneBuf, StringBuilder emailBuf,
                              StringBuilder usernameBuf, StringBuilder passwordBuf, StringBuilder confirmPasswordBuf,
                              int currencyIdx, boolean firstRender) {
        StringBuilder sb = new StringBuilder();

        // 1. Header Box Compartment
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > AUTHENTICATION GATEWAY > CUSTOMER REGISTRATION"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 2. Personal Profile Section
        sb.append(TUIBox.line("PERSONAL PROFILE", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(formatTextField("Full Legal Name", fullNameBuf.toString(), focusedField == 0, false)).append("\n");
        sb.append(formatTextField("Phone Number", phoneBuf.toString(), focusedField == 1, false)).append("\n");
        sb.append(formatTextField("Email Address", emailBuf.toString(), focusedField == 2, false)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 3. Credentials & Security Requirements Section
        sb.append(TUIBox.line("CREDENTIALS & SECURITY REQUIREMENTS", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(formatTextField("Username", usernameBuf.toString(), focusedField == 3, false)).append("\n");
        sb.append(formatTextField("Password", passwordBuf.toString(), focusedField == 4, true)).append("\n");

        // Real-time Strength Score & Rule Checklist
        PasswordEvaluation eval = PasswordValidator.evaluate(passwordBuf.toString());

        String colorizedMeter;
        if (eval.score() == 4) {
            colorizedMeter = "[" + ConsoleTheme.success(eval.meterBar()) + "] " + ConsoleTheme.success(eval.strengthLabel());
        } else if (eval.score() == 3) {
            colorizedMeter = "[" + ConsoleTheme.info(eval.meterBar()) + "] " + ConsoleTheme.info(eval.strengthLabel());
        } else if (eval.score() == 2) {
            colorizedMeter = "[" + ConsoleTheme.warning(eval.meterBar()) + "] " + ConsoleTheme.warning(eval.strengthLabel());
        } else {
            colorizedMeter = "[" + ConsoleTheme.error(eval.meterBar()) + "] " + ConsoleTheme.error(eval.strengthLabel());
        }
        String strengthRow = String.format("  %-21s: %s", "Strength Score", colorizedMeter);
        sb.append(TUIBox.line(strengthRow, width)).append("\n");

        String r1 = (eval.lengthMet() ? ConsoleTheme.success("[✔]") : ConsoleTheme.muted("[ ]")) + " 8+ Chars";
        String r2 = (eval.upperLowerMet() ? ConsoleTheme.success("[✔]") : ConsoleTheme.muted("[ ]")) + " Upper/Lower";
        String r3 = (eval.numberMet() ? ConsoleTheme.success("[✔]") : ConsoleTheme.muted("[ ]")) + " Number";
        String r4 = (eval.symbolMet() ? ConsoleTheme.success("[✔]") : ConsoleTheme.muted("[ ]")) + " Sym";
        String checklistContent = String.format("%s   %s   %s   %s", r1, r2, r3, r4);
        String checklistRow = String.format("  %-21s: %s", "Rule Checklist", checklistContent);
        sb.append(TUIBox.line(checklistRow, width)).append("\n");

        sb.append(formatTextField("Confirm Password", confirmPasswordBuf.toString(), focusedField == 5, true)).append("\n");

        String currDisplay = (currencyIdx == 0)
                ? "(1) USD - US Dollar"
                : "(2) KHR - Cambodian Riel";
        sb.append(formatTextField("Primary Currency", currDisplay, focusedField == 6, false)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 4. Action Section
        sb.append(TUIBox.line("ACTION", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String btn1 = (focusedField == 7 && actionIdx == 0)
                ? ("▸ " + ConsoleTheme.highlight("[1] Create Bank Profile"))
                : ("  [1] Create Bank Profile");
        String btn2 = (focusedField == 7 && actionIdx == 1)
                ? ("▸ " + ConsoleTheme.highlight("[2] Cancel & Return"))
                : ("  [2] Cancel & Return");

        String actionRow = "  " + btn1 + "                       " + btn2;
        sb.append(TUIBox.line(actionRow, width)).append("\n");

        // 5. Status Compartment
        sb.append(TUIBox.divider(width)).append("\n");
        String statusText;
        if (statusMessage != null) {
            statusText = isErrorStatus ? ConsoleTheme.error("Status: " + statusMessage) : ConsoleTheme.success("Status: " + statusMessage);
        } else if (eval.isValid()) {
            statusText = "Status: Password satisfies enterprise security complexity requirements.";
        } else if (!passwordBuf.isEmpty()) {
            statusText = ConsoleTheme.warning("Status: Password must satisfy all 4 security requirements.");
        } else {
            statusText = "Status: Complete profile and credentials to create account.";
        }
        sb.append(TUIBox.line(statusText, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // 6. Navigation Guide
        sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Confirm/Select  •  [1/2] Action  •  [Esc] Cancel")).append("\n");

        ScreenRenderer.render(sb.toString(), firstRender);
    }

    private String formatTextField(String label, String value, boolean isFocused, boolean isPassword) {
        String displayVal = isPassword ? "•".repeat(value.length()) : value;
        if (isFocused) {
            displayVal = displayVal + "_";
        }
        if (displayVal.length() > 49) {
            displayVal = displayVal.substring(0, 49);
        }
        String padded = String.format("%-49s", displayVal);
        String bracketContent = isFocused ? ConsoleTheme.highlight(padded) : padded;
        String row = String.format("  %-21s: [ %s ]", label, bracketContent);
        return TUIBox.line(row, TUILayout.APP_WIDTH);
    }

    private boolean submitRegistration(Terminal terminal, Attributes origAttributes, NonBlockingReader reader,
                                       ScreenNavigator navigator, TUISession session,
                                       String fullNameRaw, String phoneRaw, String emailRaw,
                                       String usernameRaw, String passwordRaw, String confirmPasswordRaw,
                                       Currency primaryCurrency) {
        String fullName = fullNameRaw.trim();
        String phone = phoneRaw.trim();
        String email = emailRaw.trim();
        String username = usernameRaw.trim();
        String password = passwordRaw;
        String confirmPassword = confirmPasswordRaw;

        if (fullName.isEmpty()) {
            this.statusMessage = "Full Legal Name is required.";
            this.isErrorStatus = true;
            return false;
        }
        if (phone.isEmpty()) {
            this.statusMessage = "Phone number is required.";
            this.isErrorStatus = true;
            return false;
        }
        if (email.isEmpty() || !email.contains("@") || !email.contains(".")) {
            this.statusMessage = "Please provide a valid email address.";
            this.isErrorStatus = true;
            return false;
        }
        if (username.length() < 3) {
            this.statusMessage = "Username must contain at least 3 characters.";
            this.isErrorStatus = true;
            return false;
        }

        PasswordEvaluation eval = PasswordValidator.evaluate(password);
        if (!eval.isValid()) {
            this.statusMessage = "Password must satisfy all 4 security rules.";
            this.isErrorStatus = true;
            return false;
        }

        if (!password.equals(confirmPassword)) {
            this.statusMessage = "Confirm Password does not match.";
            this.isErrorStatus = true;
            return false;
        }

        try {
            UserDTO newUser = authController.register(username, email, password, fullName, phone, primaryCurrency);
            renderSuccessScreen(newUser, primaryCurrency, reader);
            terminal.setAttributes(origAttributes);
            navigator.clearAndPush(new LoginScreen());
            return true;
        } catch (Exception e) {
            this.statusMessage = "Registration failed: " + e.getMessage();
            this.isErrorStatus = true;
            return false;
        }
    }

    private void renderSuccessScreen(UserDTO newUser, Currency currency, NonBlockingReader reader) throws IOException {
        int width = TUILayout.APP_WIDTH;
        StringBuilder successSb = new StringBuilder();
        successSb.append(TUIBox.top(width)).append("\n");
        successSb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > REGISTRATION COMPLETE"), width)).append("\n");
        successSb.append(TUIBox.divider(width)).append("\n");
        successSb.append(TUIBox.emptyLine(width)).append("\n");
        successSb.append(TUIBox.center(ConsoleTheme.success("✔ Registration Approved & Primary Account Provisioned!"), width)).append("\n");
        successSb.append(TUIBox.emptyLine(width)).append("\n");
        successSb.append(TUIBox.line("  Customer Name    : " + ConsoleTheme.highlight(newUser.getFullName()), width)).append("\n");
        successSb.append(TUIBox.line("  Username         : " + newUser.getUsername(), width)).append("\n");
        successSb.append(TUIBox.line("  Primary Currency : " + currency.name(), width)).append("\n");
        successSb.append(TUIBox.line("  Initial Balance  : " + (currency == Currency.KHR ? "៛ 0" : "$ 0.00"), width)).append("\n");
        successSb.append(TUIBox.emptyLine(width)).append("\n");
        successSb.append(TUIBox.divider(width)).append("\n");
        successSb.append(TUIBox.line("Note: Passwords hashed via BCrypt. Account number generated automatically.", width)).append("\n");
        successSb.append(TUIBox.bottom(width)).append("\n");
        successSb.append(ConsoleTheme.keyGuide("Press [Enter] to proceed to Sign In...")).append("\n");
        ScreenRenderer.render(successSb.toString(), true);

        while (true) {
            KeyEvent ev = TUIFormHelper.readKey(reader);
            if (ev.action() == KeyAction.ENTER || ev.action() == KeyAction.ESCAPE) {
                break;
            }
        }
    }
}
