package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AuthController;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * SCREEN 3: CUSTOMER REGISTRATION (OPEN ACCOUNT) (82 Columns)
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
        session.clearScreen();
        int width = TUILayout.APP_WIDTH;

        // Render Screen 3 Mockup Frame
        System.out.println(TUIBox.top(width));
        System.out.println(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > NEW CUSTOMER REGISTRATION"), width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.line("CUSTOMER PROFILE (users table)", width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.line("  1. Full Legal Name    : [                                                ]", width));
        System.out.println(TUIBox.line("  2. Desired Username   : [                                                ]", width));
        System.out.println(TUIBox.line("  3. Email Address      : [                                                ]", width));
        System.out.println(TUIBox.line("  4. Phone Number       : [                                                ]", width));
        System.out.println(TUIBox.line("  5. Password           : [                                                ]", width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.line("INITIAL ACCOUNT (accounts table)", width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.line("  6. Account Type       : [ (•) SAVINGS      ( ) CHECKING                  ]", width));
        System.out.println(TUIBox.line("  7. Primary Currency   : [ (•) USD          ( ) KHR                       ]", width));
        System.out.println(TUIBox.line("  8. Initial Deposit    : [ $ 100.00                                       ]", width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.line("  ► " + ConsoleTheme.highlight("[SUBMIT REGISTRATION]") + "                   " + ConsoleTheme.muted("[CANCEL & RETURN]"), width));
        System.out.println(TUIBox.bottom(width));
        System.out.println(" Passwords encrypted via BCrypt ($2a$12$). Account numbers are auto-generated.");
        if (statusMessage != null) {
            String msg = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
            System.out.println(" Status: " + msg);
            statusMessage = null;
        }
        System.out.println(TUIBox.rule(width));

        // Prompt inputs
        String fullName = ConsolePrompt.promptText("1. Full Legal Name (or '0' to return)");
        if (fullName.isEmpty() || "0".equals(fullName) || "cancel".equalsIgnoreCase(fullName)) {
            navigator.pop();
            return;
        }

        String username = ConsolePrompt.promptText("2. Desired Username");
        String email = ConsolePrompt.promptText("3. Email Address");
        String phone = ConsolePrompt.promptText("4. Phone Number");
        String password = ConsolePrompt.promptPassword("5. Password (min 8 characters)");

        if (password.length() < 8) {
            this.statusMessage = "Password must contain at least 8 characters.";
            this.isErrorStatus = true;
            return;
        }

        String accTypeStr = ConsolePrompt.promptOptional("6. Account Type [1: SAVINGS, 2: CHECKING]", "1");
        AccountType accType = "2".equals(accTypeStr.trim()) ? AccountType.CHECKING : AccountType.SAVINGS;

        String currStr = ConsolePrompt.promptOptional("7. Primary Currency [1: USD, 2: KHR]", "1");
        Currency currency = "2".equals(currStr.trim()) ? Currency.KHR : Currency.USD;

        BigDecimal depositAmount = ConsolePrompt.promptAmountOptional("8. Initial Deposit Amount (" + currency + ")", BigDecimal.ZERO);

        boolean confirm = ConsolePrompt.promptConfirmation("Submit registration and open account?");
        if (!confirm) {
            this.statusMessage = "Registration cancelled by user.";
            this.isErrorStatus = false;
            navigator.pop();
            return;
        }

        try {
            UserDTO newUser = authController.register(username, email, password, fullName, phone);

            // Fetch the user entity and provision custom account type/currency/deposit if needed
            Optional<User> userEntityOpt = ControllerFactory.getUserRepository().findById(newUser.getUserId());
            if (userEntityOpt.isPresent()) {
                User userEntity = userEntityOpt.get();
                List<Account> accounts = ControllerFactory.getAccountRepository().findByUserId(userEntity.getUserId());
                if (!accounts.isEmpty()) {
                    Account defaultAcc = accounts.get(0);
                    // Update default account if user picked SAVINGS or KHR
                    defaultAcc.setAccountType(accType);
                    defaultAcc.setCurrency(currency);
                    ControllerFactory.getAccountRepository().save(defaultAcc);

                    if (depositAmount != null && depositAmount.compareTo(BigDecimal.ZERO) > 0) {
                        ControllerFactory.getAccountService().deposit(
                                defaultAcc.getAccountId(), depositAmount, currency, "Initial opening deposit", null, userEntity);
                    }
                }
            }

            session.clearScreen();
            System.out.println(TUIBox.top(width));
            System.out.println(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > REGISTRATION COMPLETE"), width));
            System.out.println(TUIBox.divider(width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.center(ConsoleTheme.success("✔ Registration Approved & Initial Account Provisioned!"), width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.line("  Customer Name : " + ConsoleTheme.highlight(newUser.getFullName()), width));
            System.out.println(TUIBox.line("  Username      : " + newUser.getUsername(), width));
            System.out.println(TUIBox.line("  Account Type  : " + accType + " (" + currency + ")", width));
            System.out.println(TUIBox.line("  Initial Fund  : " + ConsoleFormatter.formatCurrency(depositAmount) + " " + currency, width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.bottom(width));
            System.out.println(" Status: Account successfully created. Proceeding to Sign In...");
            System.out.println(TUIBox.rule(width));

            ConsolePrompt.pause("Press Enter to proceed to Sign In...");
            navigator.clearAndPush(new LoginScreen());

        } catch (Exception e) {
            this.statusMessage = "Registration failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }
}

