package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.security.SessionManager;

import java.util.List;

/**
 * Screen for managing checking and savings accounts.
 */
public class AccountScreen implements Screen {
    private final AccountController accountController;
    private String statusMessage;
    private boolean isErrorStatus;

    public AccountScreen() {
        this(ControllerFactory.getAccountController());
    }

    public AccountScreen(AccountController accountController) {
        this.accountController = accountController;
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
        TUILayout.printScreenTitle("My Bank Accounts");

        if (statusMessage != null) {
            TUILayout.printAlert(statusMessage, isErrorStatus);
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            statusMessage = null;
        }

        List<AccountDTO> accounts = null;
        try {
            accounts = accountController.getAccountsForUser(userEntity);
        } catch (Exception e) {
            TUILayout.printAlert("Failed to fetch accounts: " + e.getMessage(), true);
        }

        // Render Accounts Table
        ConsoleTable table = new ConsoleTable()
                .addColumn("Account Number", false)
                .addColumn("Type", false)
                .addColumn("Balance", true)
                .addColumn("Currency", false)
                .addColumn("Status", false);

        if (accounts != null && !accounts.isEmpty()) {
            AccountDTO activeAcc = session.getCurrentAccount();
            for (AccountDTO acc : accounts) {
                boolean isActive = activeAcc != null && activeAcc.getAccountId().equals(acc.getAccountId());
                String accNum = (isActive ? ConsoleTheme.BRAND_GREEN + "● " : "  ") + acc.getAccountNumber();
                String type = acc.getAccountType() != null ? acc.getAccountType().name() : "N/A";
                String balance = ConsoleFormatter.formatCurrency(acc.getBalance());
                String curr = acc.getCurrency() != null ? acc.getCurrency().name() : "USD";
                String status = "ACTIVE".equalsIgnoreCase(String.valueOf(acc.getStatus()))
                        ? ConsoleTheme.success("ACTIVE")
                        : ConsoleTheme.warning(String.valueOf(acc.getStatus()));

                table.addRow(accNum, type, balance, curr, status);
            }
            table.print();
        } else {
            System.out.println(TUIBox.center(ConsoleTheme.muted("No bank accounts found for this user."), TUILayout.APP_WIDTH));
        }

        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

        ConsoleMenu menu = new ConsoleMenu()
                .addItem("01", "Open New Account", "Create a new Savings or Checking account")
                .addItem("02", "Select Active Account", "Set default account for quick operations")
                .addItem("03", "Back to Main Menu", "Return to customer dashboard");

        ConsoleMenu.MenuItem selected = menu.select();
        if (selected == null || "03".equals(selected.getCode())) {
            navigator.pop();
            return;
        }

        if ("01".equals(selected.getCode())) {
            handleCreateAccount(userEntity, session);
        } else if ("02".equals(selected.getCode())) {
            handleSelectActiveAccount(accounts, session);
        }
    }

    private void handleCreateAccount(User userEntity, TUISession session) {
        session.clearScreen();
        TUILayout.printHeader("Account Setup");
        TUILayout.printScreenTitle("Open New Bank Account");

        System.out.println(TUIBox.line(ConsoleTheme.info("Select account type:"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("  1. SAVINGS", TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("  2. CHECKING", TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

        String typeChoice = ConsolePrompt.promptText("Account Type [1 or 2]");
        AccountType type = "2".equals(typeChoice) ? AccountType.CHECKING : AccountType.SAVINGS;

        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line(ConsoleTheme.info("Select currency:"), TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("  1. USD (United States Dollar)", TUILayout.APP_WIDTH));
        System.out.println(TUIBox.line("  2. KHR (Cambodian Riel)", TUILayout.APP_WIDTH));
        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

        String currChoice = ConsolePrompt.promptText("Currency [1 or 2]");
        Currency currency = "2".equals(currChoice) ? Currency.KHR : Currency.USD;

        boolean confirm = ConsolePrompt.promptConfirmation("Confirm opening " + type + " account in " + currency + "?");
        if (!confirm) {
            this.statusMessage = "Account creation cancelled.";
            this.isErrorStatus = false;
            return;
        }

        try {
            AccountDTO newAcc = accountController.createAccount(userEntity, type, currency);
            if (session.getCurrentAccount() == null) {
                session.setCurrentAccount(newAcc);
            }
            this.statusMessage = "Account opened successfully! Account Number: " + newAcc.getAccountNumber();
            this.isErrorStatus = false;
        } catch (Exception e) {
            this.statusMessage = "Failed to create account: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }

    private void handleSelectActiveAccount(List<AccountDTO> accounts, TUISession session) {
        if (accounts == null || accounts.isEmpty()) {
            this.statusMessage = "No accounts available to select.";
            this.isErrorStatus = true;
            return;
        }

        session.clearScreen();
        TUILayout.printHeader("Active Account");
        TUILayout.printScreenTitle("Select Primary Account");

        ConsoleMenu menu = new ConsoleMenu();
        for (int i = 0; i < accounts.size(); i++) {
            AccountDTO acc = accounts.get(i);
            String code = String.format("%02d", i + 1);
            menu.addItem(code, acc.getAccountNumber() + " (" + acc.getAccountType() + ")",
                    ConsoleFormatter.formatCurrency(acc.getBalance()) + " " + acc.getCurrency());
        }

        ConsoleMenu.MenuItem selected = menu.select();
        if (selected != null) {
            int idx = Integer.parseInt(selected.getCode()) - 1;
            if (idx >= 0 && idx < accounts.size()) {
                AccountDTO chosen = accounts.get(idx);
                session.setCurrentAccount(chosen);
                this.statusMessage = "Active account set to: " + chosen.getAccountNumber();
                this.isErrorStatus = false;
            }
        }
    }
}
