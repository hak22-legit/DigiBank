package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.FinancialController;
import com.bank.controller.TransactionController;
import com.bank.model.FinancialDashboard;
import com.bank.model.FinancialInsights;
import com.bank.model.TransactionView;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.User;
import com.bank.model.enums.HistoryFilter;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.enums.TransactionStatus;
import com.bank.security.SessionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UserDashboardScreen implements Screen {
    private final FinancialController financialController;
    private final TransactionController transactionController;

    public UserDashboardScreen() {
        this(ControllerFactory.getFinancialController(), ControllerFactory.getTransactionController());
    }

    public UserDashboardScreen(FinancialController financialController) {
        this(financialController, ControllerFactory.getTransactionController());
    }

    public UserDashboardScreen(FinancialController financialController, TransactionController transactionController) {
        this.financialController = financialController;
        this.transactionController = transactionController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        UserDTO userDto = session.getCurrentUser();
        if (userDto == null) {
            navigator.pop();
            return;
        }

        session.clearScreen();
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")).toUpperCase();
        TUILayout.printHeader(userDto.getFullName());
        TUILayout.printScreenTitle("Financial Dashboard — " + dateStr);

        try {
            // Retrieve aggregated dashboard view model using User entity from SessionManager
            User userEntity = SessionManager.getCurrentUser();
            FinancialDashboard dashboard = (financialController != null && userEntity != null)
                    ? financialController.getDashboard(userEntity)
                    : null;

            BigDecimal totalBalance = dashboard != null && dashboard.getTotalBalance() != null
                    ? dashboard.getTotalBalance()
                    : BigDecimal.ZERO;
            int accountCount = dashboard != null && dashboard.getAccounts() != null
                    ? dashboard.getAccounts().size()
                    : 0;

            FinancialInsights insights = dashboard != null ? dashboard.getInsights() : null;
            BigDecimal monthlyIncome = insights != null && insights.getTotalIncome() != null
                    ? insights.getTotalIncome()
                    : BigDecimal.ZERO;
            BigDecimal monthlyExpenses = insights != null && insights.getTotalExpenses() != null
                    ? insights.getTotalExpenses()
                    : BigDecimal.ZERO;
            BigDecimal monthlySavings = insights != null && insights.getMonthlySavings() != null
                    ? insights.getMonthlySavings()
                    : monthlyIncome.subtract(monthlyExpenses);

            // Summary Card Layout
            System.out.println(TUIBox.line(
                    ConsoleTheme.BOLD + ConsoleTheme.FG_BRIGHT_WHITE + "TOTAL BALANCE" + ConsoleTheme.RESET +
                            "                        " +
                            ConsoleTheme.BOLD + ConsoleTheme.FG_BRIGHT_WHITE + "MONTHLY SUMMARY" + ConsoleTheme.RESET,
                    TUILayout.APP_WIDTH
            ));

            System.out.println(TUIBox.line(
                    ConsoleTheme.BOLD + ConsoleTheme.BRAND_GOLD + String.format("%-32s", ConsoleFormatter.formatCurrency(totalBalance)) + ConsoleTheme.RESET +
                            "Income     " + ConsoleTheme.success(String.format("%14s", ConsoleFormatter.formatCurrency(monthlyIncome))),
                    TUILayout.APP_WIDTH
            ));

            System.out.println(TUIBox.line(
                    ConsoleTheme.muted(String.format("%-32s", accountCount + " Active Account(s)")) +
                            "Expenses   " + ConsoleTheme.error(String.format("%14s", ConsoleFormatter.formatCurrency(monthlyExpenses))),
                    TUILayout.APP_WIDTH
            ));

            System.out.println(TUIBox.line(
                    "                                " +
                            "Net Savings" + ConsoleTheme.highlight(String.format("%14s", ConsoleFormatter.formatCurrency(monthlySavings))),
                    TUILayout.APP_WIDTH
            ));

            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            System.out.println(TUIBox.divider(TUILayout.APP_WIDTH));
            System.out.println(TUIBox.line(ConsoleTheme.BOLD + ConsoleTheme.FG_BRIGHT_WHITE + "RECENT TRANSACTIONS" + ConsoleTheme.RESET, TUILayout.APP_WIDTH));
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

            // Transactions Table
            ConsoleTable table = new ConsoleTable()
                    .addColumn("Date", false)
                    .addColumn("Type", false)
                    .addColumn("Amount", true)
                    .addColumn("Status", false);

            List<TransactionView> recentTxs = null;
            if (dashboard != null && dashboard.getAccounts() != null && !dashboard.getAccounts().isEmpty() && transactionController != null && userEntity != null) {
                Long primaryAccountId = dashboard.getAccounts().get(0).getAccountId();
                recentTxs = transactionController.getTransactionHistory(primaryAccountId, HistoryFilter.ALL, userEntity);
            }

            if (recentTxs != null && !recentTxs.isEmpty()) {
                // Display latest up to 5 transactions
                int count = Math.min(recentTxs.size(), 5);
                for (int i = 0; i < count; i++) {
                    TransactionView txView = recentTxs.get(i);
                    var tx = txView.getTransaction();
                    String date = tx.getTransactionDate() != null
                            ? tx.getTransactionDate().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                            : "N/A";
                    String type = tx.getTransactionType() != null ? tx.getTransactionType().name() : "TX";
                    BigDecimal displayAmount = txView.getDirection() == TransactionDirection.INCOME
                            ? tx.getAmount()
                            : tx.getAmount().negate();
                    String amount = ConsoleFormatter.formatSignedCurrency(displayAmount);
                    String status = tx.getStatus() == TransactionStatus.COMPLETED
                            ? ConsoleTheme.success("COMPLETED")
                            : ConsoleTheme.warning(tx.getStatus() != null ? tx.getStatus().name() : "PENDING");

                    table.addRow(date, type, amount, status);
                }
                table.print();
            } else {
                System.out.println(TUIBox.center(ConsoleTheme.muted("No recent transactions found for this account."), TUILayout.APP_WIDTH));
            }

        } catch (Exception e) {
            TUILayout.printAlert("Unable to load dashboard data: " + e.getMessage(), true);
        }

        TUILayout.printFooter("Press Enter to return to Main Menu");
        ConsolePrompt.pause();
        navigator.pop(); // Returns back to UserMainMenuScreen
    }
}