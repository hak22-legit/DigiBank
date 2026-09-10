package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.BudgetController;
import com.bank.controller.CategoryController;
import com.bank.controller.FinancialController;
import com.bank.model.BudgetView;
import com.bank.model.FinancialInsights;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Budget;
import com.bank.model.entity.Category;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen for financial insights, spending analysis, and monthly breakdown.
 */
public class InsightsScreen implements Screen {
    private final FinancialController financialController;
    private final BudgetController budgetController;
    private final CategoryController categoryController;

    public InsightsScreen() {
        this(ControllerFactory.getFinancialController(), ControllerFactory.getBudgetController(), ControllerFactory.getCategoryController());
    }

    public InsightsScreen(FinancialController financialController) {
        this(financialController, ControllerFactory.getBudgetController(), ControllerFactory.getCategoryController());
    }

    public InsightsScreen(FinancialController financialController, BudgetController budgetController, CategoryController categoryController) {
        this.financialController = financialController;
        this.budgetController = budgetController;
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
        String currentMonthStr = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")).toUpperCase();
        TUILayout.printHeader(userDto.getFullName());
        TUILayout.printScreenTitle("Financial Insights — " + currentMonthStr);

        try {
            FinancialInsights insights = financialController.getInsights(userEntity);

            BigDecimal balance = insights != null && insights.getTotalBalance() != null ? insights.getTotalBalance() : BigDecimal.ZERO;
            BigDecimal income = insights != null && insights.getTotalIncome() != null ? insights.getTotalIncome() : BigDecimal.ZERO;
            BigDecimal expenses = insights != null && insights.getTotalExpenses() != null ? insights.getTotalExpenses() : BigDecimal.ZERO;
            BigDecimal savings = insights != null && insights.getMonthlySavings() != null ? insights.getMonthlySavings() : income.subtract(expenses);
            BigDecimal rate = insights != null && insights.getSavingsRate() != null ? insights.getSavingsRate() : BigDecimal.ZERO;

            // 1. Overall Balance & Cashflow Card
            System.out.println(TUIBox.line(
                    ConsoleTheme.BOLD + ConsoleTheme.FG_BRIGHT_WHITE + "CURRENT TOTAL BALANCE" + ConsoleTheme.RESET +
                            "                 " +
                            ConsoleTheme.BOLD + ConsoleTheme.FG_BRIGHT_WHITE + "MONTHLY CASH FLOW" + ConsoleTheme.RESET,
                    TUILayout.APP_WIDTH
            ));

            System.out.println(TUIBox.line(
                    ConsoleTheme.BOLD + ConsoleTheme.BRAND_GOLD + String.format("%-33s", ConsoleFormatter.formatCurrency(balance)) + ConsoleTheme.RESET +
                            "Monthly Income:    " + ConsoleTheme.success(String.format("%12s", ConsoleFormatter.formatCurrency(income))),
                    TUILayout.APP_WIDTH
            ));

            System.out.println(TUIBox.line(
                    "                                 " +
                            "Monthly Expenses:  " + ConsoleTheme.error(String.format("%12s", ConsoleFormatter.formatCurrency(expenses))),
                    TUILayout.APP_WIDTH
            ));

            System.out.println(TUIBox.line(
                    "                                 " +
                            "Net Savings:       " + ConsoleTheme.highlight(String.format("%12s", ConsoleFormatter.formatCurrency(savings))),
                    TUILayout.APP_WIDTH
            ));

            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            System.out.println(TUIBox.divider(TUILayout.APP_WIDTH));

            // 2. Savings Rate & Top Expense Category
            String topCat = (insights != null && insights.getHighestSpendingCategory().isPresent())
                    ? insights.getHighestSpendingCategory().get()
                    : "None";
            String topAmt = (insights != null && insights.getHighestSpendingAmount().isPresent())
                    ? ConsoleFormatter.formatCurrency(insights.getHighestSpendingAmount().get())
                    : "$0.00";

            System.out.println(TUIBox.line(
                    ConsoleTheme.BOLD + ConsoleTheme.FG_BRIGHT_WHITE + "SAVINGS METRICS" + ConsoleTheme.RESET +
                            "                       " +
                            ConsoleTheme.BOLD + ConsoleTheme.FG_BRIGHT_WHITE + "TOP SPENDING CATEGORY" + ConsoleTheme.RESET,
                    TUILayout.APP_WIDTH
            ));

            String rateStr = String.format("%.1f%%", rate.doubleValue());
            String rateDisplay = rate.compareTo(BigDecimal.valueOf(20)) >= 0
                    ? ConsoleTheme.success(rateStr)
                    : (rate.compareTo(BigDecimal.ZERO) >= 0 ? ConsoleTheme.warning(rateStr) : ConsoleTheme.error(rateStr));

            System.out.println(TUIBox.line(
                    "Savings Rate:       " + rateDisplay +
                            "              " + ConsoleTheme.highlight(topCat) + " (" + ConsoleTheme.warning(topAmt) + ")",
                    TUILayout.APP_WIDTH
            ));

            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
            System.out.println(TUIBox.divider(TUILayout.APP_WIDTH));

            // 3. Category Budgets Health Check
            System.out.println(TUIBox.line(ConsoleTheme.BOLD + ConsoleTheme.FG_BRIGHT_WHITE + "ACTIVE BUDGET HEALTH CHECK" + ConsoleTheme.RESET, TUILayout.APP_WIDTH));
            System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));

            Map<Long, String> categoryNames = new HashMap<>();
            try {
                List<Category> cats = categoryController.getVisibleCategories(userEntity);
                for (Category c : cats) categoryNames.put(c.getCategoryId(), c.getName());
            } catch (Exception ignored) {}

            List<BudgetView> budgets = budgetController.getBudgetsWithUsage(userEntity);
            if (budgets != null && !budgets.isEmpty()) {
                for (BudgetView bv : budgets) {
                    Budget b = bv.getBudget();
                    String name = categoryNames.getOrDefault(b.getCategoryId(), "Category #" + b.getCategoryId());
                    String bar = ConsoleFormatter.progressBar(bv.getActualSpending(), b.getAmountLimit(), 16);
                    String stat = bv.getStatus().name();
                    String statColor = "OK".equals(stat) ? ConsoleTheme.success("OK") : ("WARNING".equals(stat) ? ConsoleTheme.warning("WARN") : ConsoleTheme.error("OVER"));

                    String row = String.format("%-18s  %s  %s  Spend: %s / Limit: %s",
                            name, bar, statColor,
                            ConsoleFormatter.formatCurrency(bv.getActualSpending()),
                            ConsoleFormatter.formatCurrency(b.getAmountLimit()));
                    System.out.println(TUIBox.line(row, TUILayout.APP_WIDTH));
                }
            } else {
                System.out.println(TUIBox.center(ConsoleTheme.muted("No active budgets found for health comparison."), TUILayout.APP_WIDTH));
            }

        } catch (Exception e) {
            TUILayout.printAlert("Failed to compute financial insights: " + e.getMessage(), true);
        }

        System.out.println(TUIBox.emptyLine(TUILayout.APP_WIDTH));
        TUILayout.printFooter("Press Enter to return to Main Menu");
        ConsolePrompt.pause();
        navigator.pop();
    }
}
