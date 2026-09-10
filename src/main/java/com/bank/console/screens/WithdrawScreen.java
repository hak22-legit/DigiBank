package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.CategoryController;
import com.bank.exception.InsufficientBalanceException;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Category;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;

import java.math.BigDecimal;
import java.util.List;

/**
 * Screen for withdrawing funds from an account.
 */
public class WithdrawScreen implements Screen {
    private final AccountController accountController;
    private final CategoryController categoryController;

    public WithdrawScreen() {
        this(ControllerFactory.getAccountController(), ControllerFactory.getCategoryController());
    }

    public WithdrawScreen(AccountController accountController, CategoryController categoryController) {
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
        TUILayout.printScreenTitle("Withdraw Funds");

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
            TUILayout.printAlert("No active accounts available for withdrawal.", true);
            TUILayout.printFooter("Press Enter to return");
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        // 1. Select Source Account
        System.out.println(TUIBox.line(ConsoleTheme.info("Select account to withdraw from:"), TUILayout.APP_WIDTH));
        for (int i = 0; i < accounts.size(); i++) {
            AccountDTO acc = accounts.get(i);
            String line = String.format("  [%d] %s (%s) — Available: %s %s",
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
        AccountDTO sourceAccount = accounts.get(accIdx);

        // 2. Prompt Amount
        BigDecimal amount = ConsolePrompt.promptAmount("Withdrawal Amount");

        // 3. Prompt Description
        String desc = ConsolePrompt.promptText("Description (e.g., ATM withdrawal, Expenses)");

        // 4. Optional Category
        Long categoryId = null;
        try {
            List<Category> categories = categoryController.getVisibleCategories(userEntity);
            if (!categories.isEmpty()) {
                System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
                System.out.println(TUIBox.line(ConsoleTheme.info("Expense Category (optional):"), TUILayout.APP_WIDTH));
                System.out.println(TUIBox.line("  [0] None / Skip", TUILayout.APP_WIDTH));
                for (int i = 0; i < Math.min(categories.size(), 8); i++) {
                    Category cat = categories.get(i);
                    System.out.println(TUIBox.line(String.format("  [%d] %s", i + 1, cat.getName()), TUILayout.APP_WIDTH));
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

        // 5. Review Screen
        session.clearScreen();
        TUILayout.printHeader("Review Withdrawal");
        TUILayout.printScreenTitle("Confirm Transaction Details");

        System.out.println(TUIBox.line("Account:      " + ConsoleTheme.highlight(sourceAccount.getAccountNumber() + " (" + sourceAccount.getAccountType() + ")"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("Available:    " + ConsoleFormatter.formatCurrency(sourceAccount.getBalance()) + " " + sourceAccount.getCurrency(), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("Withdrawal:   " + ConsoleTheme.error("-" + ConsoleFormatter.formatCurrency(amount)), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("Description:  " + desc, TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

        boolean confirm = ConsolePrompt.promptConfirmation("Confirm and execute withdrawal?");
        if (!confirm) {
            TUILayout.printAlert("Withdrawal cancelled by user.", false);
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        // 6. Execute Withdrawal via AccountController
        try {
            Transaction txn = accountController.withdraw(
                    sourceAccount.getAccountId(),
                    amount,
                    sourceAccount.getCurrency(),
                    desc,
                    categoryId,
                    userEntity
            );

            session.clearScreen();
            TUILayout.printHeader("Withdrawal Successful");
            TUILayout.printScreenTitle("Transaction Completed");
            TUILayout.printAlert("Withdrawal processed successfully!", false);
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            System.out.println(TUIBox.line("Transaction ID:  " + txn.getTransactionId(), TUILayout.APP_WIDTH));
            System.out.println(TUIBox.line("Debited:         " + ConsoleTheme.error("-" + ConsoleFormatter.formatCurrency(amount) + " " + sourceAccount.getCurrency()), TUILayout.APP_WIDTH));
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        } catch (InsufficientBalanceException e) {
            session.clearScreen();
            TUILayout.printHeader("Withdrawal Failed");
            TUILayout.printScreenTitle("Insufficient Balance");
            TUILayout.printAlert("Withdrawal failed: " + e.getMessage(), true);
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            System.out.println(TUIBox.line("Available Balance: " + ConsoleFormatter.formatCurrency(sourceAccount.getBalance()) + " " + sourceAccount.getCurrency(), TUILayout.APP_WIDTH));
            System.out.println(TUIBox.line("Requested Amount:  " + ConsoleTheme.error(ConsoleFormatter.formatCurrency(amount)), TUILayout.APP_WIDTH));
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        } catch (Exception e) {
            session.clearScreen();
            TUILayout.printHeader("Withdrawal Failed");
            TUILayout.printScreenTitle("Transaction Error");
            TUILayout.printAlert(e.getMessage(), true);
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        }

        TUILayout.printFooter("Press Enter to return to Main Menu");
        ConsolePrompt.pause();
        navigator.pop();
    }
}
