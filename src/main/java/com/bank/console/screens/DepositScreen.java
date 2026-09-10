package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.CategoryController;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Category;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;

import java.math.BigDecimal;
import java.util.List;

/**
 * Screen for depositing funds into an account.
 */
public class DepositScreen implements Screen {
    private final AccountController accountController;
    private final CategoryController categoryController;

    public DepositScreen() {
        this(ControllerFactory.getAccountController(), ControllerFactory.getCategoryController());
    }

    public DepositScreen(AccountController accountController, CategoryController categoryController) {
        this.accountController = accountController;
        this.categoryController = categoryController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        UserDTO userDto = session.getCurrentUser();
        User userEntity = SessionManager.getCurrentUser();
        if (userDto == null || userEntity == null) {
            navigator.pop();
            return;
        }

        session.clearScreen();
        TUILayout.printHeader(userDto.getFullName());
        TUILayout.printScreenTitle("Deposit Funds");

        List<AccountDTO> accounts;
        try {
            accounts = accountController.getAccountsForUser(userEntity);
        } catch (Exception e) {
            TUILayout.printAlert("Failed to load accounts: " + e.getMessage(), true);
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        if (accounts.isEmpty()) {
            TUILayout.printAlert("You do not have any active accounts to deposit into.", true);
            System.out.println(TUIBox.center(ConsoleTheme.muted("Please open an account first via Account Management."), TUILayout.APP_WIDTH));
            TUILayout.printFooter("Press Enter to return");
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        // 1. Select Account
        System.out.println(TUIBox.line(ConsoleTheme.info("Select destination account:"), TUILayout.APP_WIDTH));
        for (int i = 0; i < accounts.size(); i++) {
            AccountDTO acc = accounts.get(i);
            String line = String.format("  [%d] %s (%s) — Balance: %s %s",
                    i + 1, acc.getAccountNumber(), acc.getAccountType(),
                    ConsoleFormatter.formatCurrency(acc.getBalance()), acc.getCurrency());
            System.out.println(TUIBox.line(line, TUILayout.APP_WIDTH));
        }
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

        String accChoice = ConsolePrompt.promptText("Account Choice [1-" + accounts.size() + "]");
        int accIdx;
        try {
            accIdx = Integer.parseInt(accChoice) - 1;
            if (accIdx < 0 || accIdx >= accounts.size()) throw new IndexOutOfBoundsException();
        } catch (Exception e) {
            TUILayout.printAlert("Invalid account selection.", true);
            ConsolePrompt.pause();
            return;
        }
        AccountDTO targetAccount = accounts.get(accIdx);

        // 2. Prompt Amount
        BigDecimal amount = ConsolePrompt.promptAmount("Deposit Amount");

        // 3. Prompt Description
        String desc = ConsolePrompt.promptText("Description (e.g., Salary, Cash deposit)");

        // 4. Optional Category Selection
        Long categoryId = null;
        try {
            List<Category> categories = categoryController.getVisibleCategories(userEntity);
            if (!categories.isEmpty()) {
                System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
                System.out.println(TUIBox.line(ConsoleTheme.info("Select category (optional):"), TUILayout.APP_WIDTH));
                System.out.println(TUIBox.line("  [0] None / Skip", TUILayout.APP_WIDTH));
                for (int i = 0; i < Math.min(categories.size(), 8); i++) {
                    Category cat = categories.get(i);
                    String tag = cat.isSystem() ? ConsoleTheme.muted("[SYS]") : ConsoleTheme.highlight("[CUSTOM]");
                    System.out.println(TUIBox.line(String.format("  [%d] %s %s", i + 1, cat.getName(), tag), TUILayout.APP_WIDTH));
                }
                String catChoice = ConsolePrompt.promptOptional("Category Choice", "0");
                try {
                    int cIdx = Integer.parseInt(catChoice) - 1;
                    if (cIdx >= 0 && cIdx < categories.size()) {
                        categoryId = categories.get(cIdx).getCategoryId();
                    }
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception ignored) {}

        // 5. Review & Confirmation
        session.clearScreen();
        TUILayout.printHeader("Review Deposit");
        TUILayout.printScreenTitle("Confirm Transaction Details");

        System.out.println(TUIBox.line("Account:      " + ConsoleTheme.highlight(targetAccount.getAccountNumber() + " (" + targetAccount.getAccountType() + ")"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("Amount:       " + ConsoleTheme.success("+" + ConsoleFormatter.formatCurrency(amount)), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("Currency:     " + targetAccount.getCurrency(), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("Description:  " + desc, TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

        boolean confirm = ConsolePrompt.promptConfirmation("Confirm and execute deposit?");
        if (!confirm) {
            TUILayout.printAlert("Deposit cancelled by user.", false);
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        // 6. Execute Deposit via AccountController
        try {
            Transaction txn = accountController.deposit(
                    targetAccount.getAccountId(),
                    amount,
                    targetAccount.getCurrency(),
                    desc,
                    categoryId,
                    userEntity
            );

            session.clearScreen();
            TUILayout.printHeader("Deposit Successful");
            TUILayout.printScreenTitle("Transaction Completed");
            TUILayout.printAlert("Deposit completed successfully!", false);
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            System.out.println(TUIBox.line("Transaction ID:  " + txn.getTransactionId(), TUILayout.APP_WIDTH));
            System.out.println(TUIBox.line("Idempotency:     " + txn.getIdempotencyKey(), TUILayout.APP_WIDTH));
            System.out.println(TUIBox.line("Credited:        " + ConsoleTheme.success(ConsoleFormatter.formatCurrency(amount) + " " + targetAccount.getCurrency()), TUILayout.APP_WIDTH));
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        } catch (Exception e) {
            session.clearScreen();
            TUILayout.printHeader("Deposit Failed");
            TUILayout.printScreenTitle("Transaction Error");
            TUILayout.printAlert(e.getMessage(), true);
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        }

        TUILayout.printFooter("Press Enter to return to Main Menu");
        ConsolePrompt.pause();
        navigator.pop();
    }
}
