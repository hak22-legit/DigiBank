package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleMenu;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.model.dto.UserDTO;

public class UserMainMenuScreen implements Screen {

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        UserDTO user = session.getCurrentUser();
        if (user == null) {
            navigator.clearAndPush(new LoginScreen(ControllerFactory.getAuthController()));
            return;
        }

        session.clearScreen();
        TUILayout.printHeader("Welcome, " + user.getFullName());
        TUILayout.printScreenTitle("Customer Banking Portal");

        ConsoleMenu menu = new ConsoleMenu()
                .addItem("01", "Dashboard", "View financial overview & recent activity")
                .addItem("02", "Accounts", "Manage your checking and savings accounts")
                .addItem("03", "Deposit", "Deposit funds into an account")
                .addItem("04", "Withdraw", "Withdraw funds from an account")
                .addItem("05", "Transfer", "Transfer funds securely with idempotency")
                .addItem("06", "Exchange", "Convert funds between USD and KHR accounts")
                .addItem("07", "Transactions", "View and filter your transaction history")
                .addItem("08", "Categories", "Manage system and custom transaction categories")
                .addItem("09", "Budgets", "Set spending limits and monitor budget usage")
                .addItem("10", "Saving Goals", "Track progress toward financial targets")
                .addItem("11", "Insights", "Analyze spending breakdown and habits")
                .addItem("12", "Loans", "Apply for digital loans and view status")
                .addItem("13", "Repay Loan", "View installment schedules and make repayments")
                .addItem("14", "Statement", "Generate official PDF account statement")
                .addItem("15", "Logout", "Sign out of your banking session");

        ConsoleMenu.MenuItem selected = menu.select();

        if (selected == null || "15".equals(selected.getCode())) {
            boolean confirm = ConsolePrompt.promptConfirmation("Are you sure you want to sign out?");
            if (confirm) {
                ControllerFactory.getAuthController().logoutUser();
                session.logout();
                navigator.clearAndPush(new LoginScreen(ControllerFactory.getAuthController()));
            }
            return;
        }

        switch (selected.getCode()) {
            case "01" -> navigator.push(new UserDashboardScreen());
            case "02" -> navigator.push(new AccountScreen());
            case "03" -> navigator.push(new DepositScreen());
            case "04" -> navigator.push(new WithdrawScreen());
            case "05" -> navigator.push(new TransferScreen());
            case "06" -> navigator.push(new ExchangeScreen());
            case "07" -> navigator.push(new TransactionHistoryScreen());
            case "08" -> navigator.push(new CategoryManagementScreen());
            case "09" -> navigator.push(new BudgetScreen());
            case "10" -> navigator.push(new SavingGoalScreen());
            case "11" -> navigator.push(new InsightsScreen());
            case "12" -> navigator.push(new LoanScreen());
            case "13" -> navigator.push(new LoanRepaymentScreen());
            case "14" -> navigator.push(new StatementScreen());
        }
    }
}