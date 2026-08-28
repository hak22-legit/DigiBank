package com.bank;

import com.bank.database.DatabaseConnection;
import com.bank.enums.AccountType;
import com.bank.enums.Currency;
import com.bank.enums.HistoryFilter;
import com.bank.exception.*;
import com.bank.model.*;
import com.bank.repository.*;
import com.bank.security.SessionManager;
import com.bank.service.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Enterprise Banking System");
        System.out.println("  Phase 1-12 Smoke Test");
        System.out.println("========================================");

        // ---------------------------------------------------
        // Connection sanity check
        // ---------------------------------------------------
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM categories")) {

            if (rs.next()) {
                System.out.println("Database connection: SUCCESS");
                System.out.println("Categories in database: " + rs.getInt(1));
            }
        } catch (Exception e) {
            System.err.println("Database connection: FAILED");
            e.printStackTrace();
        }

        // ---------------------------------------------------
        // Repositories
        // ---------------------------------------------------
        UserRepository userRepo = new UserRepositoryImpl();
        AccountRepository accountRepo = new AccountRepositoryImpl();
        TransactionRepository transactionRepo = new TransactionRepositoryImpl();
        CategoryRepository categoryRepo = new CategoryRepositoryImpl();

        // ---------------------------------------------------
        // Services
        // ---------------------------------------------------
        AccountService accountService = new AccountService(accountRepo, transactionRepo);
        AuthService authService = new AuthService(userRepo, accountService);
        TransactionService transactionService = new TransactionService(transactionRepo, accountRepo);
        CategoryService categoryService = new CategoryService(categoryRepo);

        // ---------------------------------------------------
        // Auth (idempotent - won't re-register if user exists)
        // ---------------------------------------------------
        User loggedIn;
        Optional<User> existing = userRepo.findByUsername("johndoe");
        if (existing.isEmpty()) {
            User newUser = authService.register(
                    "johndoe", "john@example.com", "SecurePass123", "John Doe", "0123456789");
            System.out.println("Registered: " + newUser.getUsername() + " (id=" + newUser.getUserId() + ")");
            System.out.println("Default account auto-created on registration.");
        }
        loggedIn = authService.login("johndoe", "SecurePass123");
        System.out.println("Login success: " + loggedIn.getFullName());

        // ---------------------------------------------------
        // Accounts overview
        // ---------------------------------------------------
        List<Account> myAccounts = accountService.getAccountsForUser(loggedIn);
        System.out.println("Accounts for user: " + myAccounts.size());
        for (Account acc : myAccounts) {
            System.out.println("  - " + acc.getAccountNumber() + " | " + acc.getAccountType()
                    + " | " + acc.getCurrency() + " | Balance: " + acc.getBalance());
        }
        Account primaryAccount = myAccounts.get(0);

        // ---------------------------------------------------
        // Categories overview (Phase 12)
        // ---------------------------------------------------
        List<Category> visibleCategories = categoryService.getVisibleCategories(loggedIn);
        System.out.println("Visible categories: " + visibleCategories.size());

        // ---------------------------------------------------
        // Transaction history snapshot (Phase 11)
        // ---------------------------------------------------
        List<TransactionView> history = transactionService.getTransactionHistory(
                primaryAccount.getAccountId(), HistoryFilter.ALL, loggedIn);
        System.out.println("Transaction history for primary account: " + history.size() + " entries");

        BigDecimal currentBalance = accountService.getBalance(primaryAccount.getAccountId(), loggedIn);
        System.out.println("Current primary balance: " + currentBalance);

        // =====================================================
        // PHASE 13+ TEST CODE GOES BELOW THIS LINE
        // =====================================================

        FinancialInsightsService insightsService = new FinancialInsightsService(
                accountRepo, transactionService, categoryService);

        FinancialInsights insights = insightsService.getCurrentMonthInsights(loggedIn);
        System.out.println("=== Financial Insights (This Month) ===");
        System.out.println("Total balance: " + insights.getTotalBalance());
        System.out.println("Total income: " + insights.getTotalIncome());
        System.out.println("Total expenses: " + insights.getTotalExpenses());
        System.out.println("Monthly savings: " + insights.getMonthlySavings());
        System.out.println("Savings rate: " + insights.getSavingsRate() + "%");
        System.out.println("Highest spending category: " +
                insights.getHighestSpendingCategory().orElse("No categorized expenses yet"));

        BudgetRepository budgetRepo = new BudgetRepositoryImpl();
        BudgetService budgetService = new BudgetService(budgetRepo, accountRepo, transactionService, categoryService);

// រក category "Food" ដែលជា system category
        Category food = categoryService.getVisibleCategories(loggedIn).stream()
                .filter(c -> c.getName().equals("Food")).findFirst().orElseThrow();

        Budget foodBudget = budgetService.createBudget(
                loggedIn, food.getCategoryId(), new BigDecimal("200"),
                com.bank.enums.BudgetPeriod.MONTHLY,
                java.time.LocalDate.now().withDayOfMonth(1),
                java.time.LocalDate.now());

        System.out.println("Budget created: " + foodBudget.getAmountLimit() + " for category " + food.getName());

// ដកប្រាក់ជាមួយ category Food ដើម្បីសាកល្បង usage
        accountService.withdraw(primaryAccount.getAccountId(), new BigDecimal("170"),
                Currency.USD, "Groceries", food.getCategoryId(), loggedIn);

        BudgetView view = budgetService.getBudgetUsage(foodBudget.getBudgetId(), loggedIn);
        System.out.println("Actual spending: " + view.getActualSpending());
        System.out.println("Usage: " + view.getUsagePercentage() + "%");
        System.out.println("Status: " + view.getStatus());
        System.out.println("Remaining: " + view.getRemainingAmount());

        // =====================================================
        // End of test code
        // =====================================================

        // ---------------------------------------------------
        // Logout + pool shutdown (MUST be last)
        // ---------------------------------------------------
        authService.logout();
        System.out.println("Session after logout: " + SessionManager.isUserLoggedIn());

        DatabaseConnection.closePool();
    }
}