package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
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
        int width = TUILayout.APP_WIDTH;
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > NEW CUSTOMER REGISTRATION"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("CUSTOMER PROFILE (users table)", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.line("  1. Full Legal Name    : [                                                ]", width)).append("\n");
        sb.append(TUIBox.line("  2. Desired Username   : [                                                ]", width)).append("\n");
        sb.append(TUIBox.line("  3. Email Address      : [                                                ]", width)).append("\n");
        sb.append(TUIBox.line("  4. Phone Number       : [                                                ]", width)).append("\n");
        sb.append(TUIBox.line("  5. Password           : [                                                ]", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("INITIAL ACCOUNT (accounts table)", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.line("  6. Account Type       : [ (•) SAVINGS      ( ) CHECKING                  ]", width)).append("\n");
        sb.append(TUIBox.line("  7. Primary Currency   : [ (•) USD          ( ) KHR                       ]", width)).append("\n");
        sb.append(TUIBox.line("  8. Initial Deposit    : [ $ 100.00                                       ]", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("  ► " + ConsoleTheme.highlight("[SUBMIT REGISTRATION]") + "                   " + ConsoleTheme.muted("[CANCEL & RETURN]"), width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");
        sb.append(" Passwords encrypted via BCrypt ($2a$12$). Account numbers are auto-generated.").append("\n");
        if (statusMessage != null) {
            String msg = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
            sb.append(" Status: ").append(msg).append("\n");
            statusMessage = null;
        }
        sb.append(TUIBox.rule(width)).append("\n");

        ScreenRenderer.render(sb.toString(), true);

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
            successSb.append(TUIBox.bottom(width)).append("\n");
            successSb.append(" Status: Account successfully created. Proceeding to Sign In...").append("\n");
            successSb.append(TUIBox.rule(width)).append("\n");
            ScreenRenderer.render(successSb.toString(), true);

            ConsolePrompt.pause("Press Enter to proceed to Sign In...");
            navigator.clearAndPush(new LoginScreen());

        } catch (Exception e) {
            this.statusMessage = "Registration failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }
}

