package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AuthController;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * SCREEN 3: CUSTOMER REGISTRATION (OPEN ACCOUNT) (82 Columns)
 * Interactive keyboard form navigation, radio button toggling, masked password entry,
 * strict 82-column enclosed container, and zero console prompt leaks.
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

        // 8 editable fields (0..7) + 2 action buttons (8: Submit, 9: Cancel)
        int focusedField = 0;

        StringBuilder fullNameBuf = new StringBuilder();
        StringBuilder usernameBuf = new StringBuilder();
        StringBuilder emailBuf = new StringBuilder();
        StringBuilder phoneBuf = new StringBuilder();
        StringBuilder passwordBuf = new StringBuilder();
        AccountType accountType = AccountType.SAVINGS;
        Currency currency = Currency.USD;
        StringBuilder depositBuf = new StringBuilder("100.00");

        boolean firstRender = true;

        try {
            while (true) {
                renderScreen(width, focusedField, fullNameBuf, usernameBuf, emailBuf,
                        phoneBuf, passwordBuf, accountType, currency, depositBuf, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);

                // Global Cancel via ESC
                if (event.action() == KeyAction.ESCAPE) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }

                // Field cycling: Tab, Down, Shift+Tab, Up
                if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 10;
                    statusMessage = null;
                    continue;
                } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 10) % 10;
                    statusMessage = null;
                    continue;
                }

                // Left & Right arrow handling
                if (event.action() == KeyAction.LEFT) {
                    if (focusedField == 5) {
                        accountType = (accountType == AccountType.SAVINGS) ? AccountType.CHECKING : AccountType.SAVINGS;
                    } else if (focusedField == 6) {
                        currency = (currency == Currency.USD) ? Currency.KHR : Currency.USD;
                    } else if (focusedField == 9) {
                        focusedField = 8;
                    }
                    continue;
                } else if (event.action() == KeyAction.RIGHT) {
                    if (focusedField == 5) {
                        accountType = (accountType == AccountType.SAVINGS) ? AccountType.CHECKING : AccountType.SAVINGS;
                    } else if (focusedField == 6) {
                        currency = (currency == Currency.USD) ? Currency.KHR : Currency.USD;
                    } else if (focusedField == 8) {
                        focusedField = 9;
                    }
                    continue;
                }

                // Spacebar toggling for radio buttons or text space
                if (event.action() == KeyAction.CHAR && event.ch() == ' ') {
                    if (focusedField == 5) {
                        accountType = (accountType == AccountType.SAVINGS) ? AccountType.CHECKING : AccountType.SAVINGS;
                        continue;
                    } else if (focusedField == 6) {
                        currency = (currency == Currency.USD) ? Currency.KHR : Currency.USD;
                        continue;
                    } else if (focusedField == 0) {
                        if (fullNameBuf.length() < 45) {
                            fullNameBuf.append(' ');
                        }
                        statusMessage = null;
                        continue;
                    } else if (focusedField == 3) {
                        if (phoneBuf.length() < 20) {
                            phoneBuf.append(' ');
                        }
                        statusMessage = null;
                        continue;
                    }
                }

                // Backspace handling
                if (event.action() == KeyAction.BACKSPACE) {
                    statusMessage = null;
                    switch (focusedField) {
                        case 0 -> { if (fullNameBuf.length() > 0) fullNameBuf.deleteCharAt(fullNameBuf.length() - 1); }
                        case 1 -> { if (usernameBuf.length() > 0) usernameBuf.deleteCharAt(usernameBuf.length() - 1); }
                        case 2 -> { if (emailBuf.length() > 0) emailBuf.deleteCharAt(emailBuf.length() - 1); }
                        case 3 -> { if (phoneBuf.length() > 0) phoneBuf.deleteCharAt(phoneBuf.length() - 1); }
                        case 4 -> { if (passwordBuf.length() > 0) passwordBuf.deleteCharAt(passwordBuf.length() - 1); }
                        case 7 -> { if (depositBuf.length() > 0) depositBuf.deleteCharAt(depositBuf.length() - 1); }
                    }
                    continue;
                }

                // Enter handling: advance to next field or trigger action
                if (event.action() == KeyAction.ENTER) {
                    if (focusedField < 7) {
                        focusedField++;
                    } else if (focusedField == 7) {
                        focusedField = 8;
                    } else if (focusedField == 8) {
                        boolean success = submitRegistration(terminal, origAttributes, reader, navigator, session,
                                fullNameBuf.toString(), usernameBuf.toString(), emailBuf.toString(),
                                phoneBuf.toString(), passwordBuf.toString(), accountType, currency, depositBuf.toString());
                        if (success) {
                            return;
                        }
                    } else if (focusedField == 9) {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                    continue;
                }

                // Hotkeys '1' and '2' for radio options or action buttons
                if ((event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) && (event.ch() == '1' || event.ch() == '2')) {
                    if (focusedField == 5) {
                        accountType = (event.ch() == '1') ? AccountType.SAVINGS : AccountType.CHECKING;
                        continue;
                    } else if (focusedField == 6) {
                        currency = (event.ch() == '1') ? Currency.USD : Currency.KHR;
                        continue;
                    } else if (focusedField == 8 || focusedField == 9) {
                        if (event.ch() == '1') {
                            focusedField = 8;
                            boolean success = submitRegistration(terminal, origAttributes, reader, navigator, session,
                                    fullNameBuf.toString(), usernameBuf.toString(), emailBuf.toString(),
                                    phoneBuf.toString(), passwordBuf.toString(), accountType, currency, depositBuf.toString());
                            if (success) {
                                return;
                            }
                        } else {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                        continue;
                    }
                }

                // Character and digit typing
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
                            if (usernameBuf.length() < 30 && (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-')) {
                                usernameBuf.append(c);
                            }
                        }
                        case 2 -> {
                            if (emailBuf.length() < 45 && !Character.isWhitespace(c)) {
                                emailBuf.append(c);
                            }
                        }
                        case 3 -> {
                            if (phoneBuf.length() < 20 && (Character.isDigit(c) || c == '+' || c == '-' || c == ' ' || c == '(' || c == ')')) {
                                phoneBuf.append(c);
                            }
                        }
                        case 4 -> {
                            if (passwordBuf.length() < 32 && c >= 32 && c <= 126) {
                                passwordBuf.append(c);
                            }
                        }
                        case 7 -> {
                            if ((c >= '0' && c <= '9') || (c == '.' && !depositBuf.toString().contains("."))) {
                                if (depositBuf.length() < 12) {
                                    depositBuf.append(c);
                                }
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

    private void renderScreen(int width, int focusedField,
                             StringBuilder fullNameBuf, StringBuilder usernameBuf, StringBuilder emailBuf,
                             StringBuilder phoneBuf, StringBuilder passwordBuf,
                             AccountType accountType, Currency currency, StringBuilder depositBuf,
                             boolean firstRender) {
        StringBuilder sb = new StringBuilder();

        // 1. Header Box Compartment
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > NEW CUSTOMER REGISTRATION"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 2. Customer Profile Section (No database leaks)
        sb.append(TUIBox.line("CUSTOMER PROFILE", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(formatTextField("Full Legal Name", fullNameBuf.toString(), focusedField == 0, false)).append("\n");
        sb.append(formatTextField("Desired Username", usernameBuf.toString(), focusedField == 1, false)).append("\n");
        sb.append(formatTextField("Email Address", emailBuf.toString(), focusedField == 2, false)).append("\n");
        sb.append(formatTextField("Phone Number", phoneBuf.toString(), focusedField == 3, false)).append("\n");
        sb.append(formatTextField("Password", passwordBuf.toString(), focusedField == 4, true)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 3. Initial Account Configuration Section
        sb.append(TUIBox.line("INITIAL ACCOUNT CONFIGURATION", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Account Type Radio
        String optSavings = (accountType == AccountType.SAVINGS) ? "(•) SAVINGS" : "( ) SAVINGS";
        String optChecking = (accountType == AccountType.CHECKING) ? "(•) CHECKING" : "( ) CHECKING";
        sb.append(formatRadioField("Account Type", optSavings, optChecking, focusedField == 5)).append("\n");

        // Primary Currency Radio
        String optUsd = (currency == Currency.USD) ? "(•) USD" : "( ) USD";
        String optKhr = (currency == Currency.KHR) ? "(•) KHR" : "( ) KHR";
        sb.append(formatRadioField("Primary Currency", optUsd, optKhr, focusedField == 6)).append("\n");

        // Initial Deposit
        String symbol = (currency == Currency.KHR) ? "៛" : "$";
        sb.append(formatDepositField("Initial Deposit", symbol, depositBuf.toString(), focusedField == 7)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 4. Action Buttons Section
        sb.append(TUIBox.line("ACTION", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String btn1 = (focusedField == 8)
                ? ("▸ " + ConsoleTheme.highlight("[1] Submit Registration"))
                : ("  " + (focusedField < 8 ? "[1] Submit Registration" : ConsoleTheme.muted("[1] Submit Registration")));
        String btn2 = (focusedField == 9)
                ? ("▸ " + ConsoleTheme.highlight("[2] Cancel & Return"))
                : ("  " + ConsoleTheme.muted("[2] Cancel & Return"));

        String actionRow = "  " + btn1 + "                       " + btn2;
        sb.append(TUIBox.line(actionRow, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // 5. Optional Status / Error Line inside container
        if (statusMessage != null) {
            sb.append(TUIBox.divider(width)).append("\n");
            String msg = isErrorStatus ? ConsoleTheme.error(" Error: " + statusMessage) : ConsoleTheme.success(" " + statusMessage);
            sb.append(TUIBox.line(msg, width)).append("\n");
        }

        // 6. Contained Footer Note & Border
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("Note: Passwords hashed via BCrypt. Account numbers generated automatically.", width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // 7. Standard Navigation Guide
        sb.append(ConsoleTheme.muted(" [Tab/↓] Next Field  •  [Space] Toggle Radio  •  [Enter] Confirm  •  [Esc] Cancel")).append("\n");

        ScreenRenderer.render(sb.toString(), firstRender);
    }

    private String formatTextField(String label, String value, boolean isFocused, boolean isPassword) {
        String displayVal = isPassword ? "•".repeat(value.length()) : value;
        if (isFocused) {
            displayVal = displayVal + "_";
        }
        if (displayVal.length() > 53) {
            displayVal = displayVal.substring(0, 53);
        }
        String padded = String.format("%-53s", displayVal);
        String bracketContent = isFocused ? ConsoleTheme.highlight(padded) : padded;
        String row = String.format(" %-18s: [ %s ]", label, bracketContent);
        return TUIBox.line(row, TUILayout.APP_WIDTH);
    }

    private String formatRadioField(String label, String opt1, String opt2, boolean isFocused) {
        String radioContent = String.format("%-22s %-30s", opt1, opt2);
        String bracketContent = isFocused ? ConsoleTheme.highlight(radioContent) : radioContent;
        String row = String.format(" %-18s: [ %s ]", label, bracketContent);
        return TUIBox.line(row, TUILayout.APP_WIDTH);
    }

    private String formatDepositField(String label, String symbol, String amount, boolean isFocused) {
        String displayVal = symbol + " " + amount;
        if (isFocused) {
            displayVal = displayVal + "_";
        }
        if (displayVal.length() > 53) {
            displayVal = displayVal.substring(0, 53);
        }
        String padded = String.format("%-53s", displayVal);
        String bracketContent = isFocused ? ConsoleTheme.highlight(padded) : padded;
        String row = String.format(" %-18s: [ %s ]", label, bracketContent);
        return TUIBox.line(row, TUILayout.APP_WIDTH);
    }

    private boolean submitRegistration(Terminal terminal, Attributes origAttributes, NonBlockingReader reader,
                                       ScreenNavigator navigator, TUISession session,
                                       String fullNameRaw, String usernameRaw, String emailRaw,
                                       String phoneRaw, String passwordRaw,
                                       AccountType accountType, Currency currency, String depositRaw) {
        String fullName = fullNameRaw.trim();
        String username = usernameRaw.trim();
        String email = emailRaw.trim();
        String phone = phoneRaw.trim();
        String password = passwordRaw;

        if (fullName.isEmpty()) {
            this.statusMessage = "Full Legal Name is required.";
            this.isErrorStatus = true;
            return false;
        }
        if (username.length() < 3) {
            this.statusMessage = "Username must contain at least 3 characters.";
            this.isErrorStatus = true;
            return false;
        }
        if (!email.contains("@") || !email.contains(".")) {
            this.statusMessage = "Please provide a valid email address.";
            this.isErrorStatus = true;
            return false;
        }
        if (phone.isEmpty()) {
            this.statusMessage = "Phone number is required.";
            this.isErrorStatus = true;
            return false;
        }
        if (password.length() < 8) {
            this.statusMessage = "Password must contain at least 8 characters.";
            this.isErrorStatus = true;
            return false;
        }

        BigDecimal depositAmount = BigDecimal.ZERO;
        String cleanDep = depositRaw.trim();
        if (!cleanDep.isEmpty()) {
            try {
                depositAmount = new BigDecimal(cleanDep);
                if (depositAmount.compareTo(BigDecimal.ZERO) < 0) {
                    this.statusMessage = "Initial deposit amount cannot be negative.";
                    this.isErrorStatus = true;
                    return false;
                }
            } catch (Exception e) {
                this.statusMessage = "Invalid numeric initial deposit amount.";
                this.isErrorStatus = true;
                return false;
            }
        }

        try {
            UserDTO newUser = authController.register(username, email, password, fullName, phone);

            // Fetch user entity and provision custom account type/currency/deposit if needed
            Optional<User> userEntityOpt = ControllerFactory.getUserRepository().findById(newUser.getUserId());
            if (userEntityOpt.isPresent()) {
                User userEntity = userEntityOpt.get();
                List<Account> accounts = ControllerFactory.getAccountRepository().findByUserId(userEntity.getUserId());
                if (!accounts.isEmpty()) {
                    Account defaultAcc = accounts.get(0);
                    defaultAcc.setAccountType(accountType);
                    defaultAcc.setCurrency(currency);
                    ControllerFactory.getAccountRepository().save(defaultAcc);

                    if (depositAmount.compareTo(BigDecimal.ZERO) > 0) {
                        ControllerFactory.getAccountService().deposit(
                                defaultAcc.getAccountId(), depositAmount, currency, "Initial opening deposit", null, userEntity);
                    }
                }
            }

            renderSuccessScreen(newUser, accountType, currency, depositAmount, reader);
            terminal.setAttributes(origAttributes);
            navigator.clearAndPush(new LoginScreen());
            return true;
        } catch (Exception e) {
            this.statusMessage = "Registration failed: " + e.getMessage();
            this.isErrorStatus = true;
            return false;
        }
    }

    private void renderSuccessScreen(UserDTO newUser, AccountType accType, Currency currency,
                                    BigDecimal depositAmount, NonBlockingReader reader) throws IOException {
        int width = TUILayout.APP_WIDTH;
        StringBuilder successSb = new StringBuilder();
        successSb.append(TUIBox.top(width)).append("\n");
        successSb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > REGISTRATION COMPLETE"), width)).append("\n");
        successSb.append(TUIBox.divider(width)).append("\n");
        successSb.append(TUIBox.emptyLine(width)).append("\n");
        successSb.append(TUIBox.center(ConsoleTheme.success("✔ Registration Approved & Initial Account Provisioned!"), width)).append("\n");
        successSb.append(TUIBox.emptyLine(width)).append("\n");
        successSb.append(TUIBox.line("  Customer Name : " + ConsoleTheme.highlight(newUser.getFullName()), width)).append("\n");
        successSb.append(TUIBox.line("  Username      : " + newUser.getUsername(), width)).append("\n");
        successSb.append(TUIBox.line("  Account Type  : " + accType + " (" + currency + ")", width)).append("\n");
        successSb.append(TUIBox.line("  Initial Fund  : " + ConsoleFormatter.formatCurrency(depositAmount) + " " + currency, width)).append("\n");
        successSb.append(TUIBox.emptyLine(width)).append("\n");
        successSb.append(TUIBox.divider(width)).append("\n");
        successSb.append(TUIBox.line("Note: Passwords hashed via BCrypt. Account numbers generated automatically.", width)).append("\n");
        successSb.append(TUIBox.bottom(width)).append("\n");
        successSb.append(ConsoleTheme.muted("  Press [Enter] to proceed to Sign In...")).append("\n");
        ScreenRenderer.render(successSb.toString(), true);

        while (true) {
            KeyEvent ev = TUIFormHelper.readKey(reader);
            if (ev.action() == KeyAction.ENTER || ev.action() == KeyAction.ESCAPE) {
                break;
            }
        }
    }
}
