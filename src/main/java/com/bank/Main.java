package com.bank;

import com.bank.database.DatabaseConnection;
import com.bank.enums.*;
import com.bank.exception.*;
import com.bank.model.*;
import com.bank.repository.*;
import com.bank.security.SessionManager;
import com.bank.service.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public class Main {

    private static final Scanner scanner = new Scanner(System.in);

    // Repositories
    private static final UserRepository userRepo = new UserRepositoryImpl();
    private static final AccountRepository accountRepo = new AccountRepositoryImpl();
    private static final TransactionRepository transactionRepo = new TransactionRepositoryImpl();
    private static final CategoryRepository categoryRepo = new CategoryRepositoryImpl();
    private static final BudgetRepository budgetRepo = new BudgetRepositoryImpl();
    private static final SavingGoalRepository savingGoalRepo = new SavingGoalRepositoryImpl();

    // Services
    private static final AccountService accountService = new AccountService(accountRepo, transactionRepo);
    private static final AuthService authService = new AuthService(userRepo, accountService);
    private static final TransactionService transactionService = new TransactionService(transactionRepo, accountRepo);
    private static final CategoryService categoryService = new CategoryService(categoryRepo);
    private static final BudgetService budgetService = new BudgetService(budgetRepo, accountRepo, transactionService, categoryService);
    private static final FinancialInsightsService insightsService = new FinancialInsightsService(accountRepo, transactionService, categoryService);
    private static final SavingGoalService savingGoalService = new SavingGoalService(savingGoalRepo);
    private static final DashboardService dashboardService = new DashboardService(accountRepo, insightsService, budgetService, savingGoalService);

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   DIGIBANK - Interactive Test Console");
        System.out.println("========================================");

        boolean running = true;
        while (running) {
            if (!SessionManager.isUserLoggedIn()) {
                running = showAuthMenu();
            } else {
                running = showMainMenu();
            }
        }

        DatabaseConnection.closePool();
        System.out.println("Goodbye!");
    }

    // =====================================================
    // AUTH MENU (before login)
    // =====================================================
    private static boolean showAuthMenu() {
        System.out.println("\n--- AUTH MENU ---");
        System.out.println("1. Register");
        System.out.println("2. Login");
        System.out.println("0. Exit");
        System.out.print("Choose: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> register();
            case "2" -> login();
            case "0" -> {
                return false;
            }
            default -> System.out.println("Invalid option.");
        }
        return true;
    }

    private static void register() {
        try {
            System.out.print("Username: ");
            String username = scanner.nextLine().trim();
            System.out.print("Email: ");
            String email = scanner.nextLine().trim();
            System.out.print("Password: ");
            String password = scanner.nextLine().trim();
            System.out.print("Full name: ");
            String fullName = scanner.nextLine().trim();
            System.out.print("Phone: ");
            String phone = scanner.nextLine().trim();

            User user = authService.register(username, email, password, fullName, phone);
            System.out.println("Registered successfully! ID=" + user.getUserId()
                    + " | Default CHECKING/USD account auto-created.");
        } catch (RuntimeException e) {
            System.out.println("Registration failed: " + e.getMessage());
        }
    }

    private static void login() {
        try {
            System.out.print("Username: ");
            String username = scanner.nextLine().trim();
            System.out.print("Password: ");
            String password = scanner.nextLine().trim();

            User user = authService.login(username, password);
            System.out.println("Welcome back, " + user.getFullName() + "!");
        } catch (RuntimeException e) {
            System.out.println("Login failed: " + e.getMessage());
        }
    }

    // =====================================================
    // MAIN MENU (after login)
    // =====================================================
    private static boolean showMainMenu() {
        User currentUser = SessionManager.getCurrentUser();
        System.out.println("\n--- MAIN MENU (" + currentUser.getFullName() + ") ---");
        System.out.println("1. View my accounts");
        System.out.println("2. Create new account");
        System.out.println("3. Deposit");
        System.out.println("4. Withdraw");
        System.out.println("5. Transfer");
        System.out.println("6. Transaction history");
        System.out.println("7. Categories");
        System.out.println("8. Budgets");
        System.out.println("9. Financial insights");
        System.out.println("10. Saving goals");
        System.out.println("11. Financial dashboard");
        System.out.println("12. Logout");
        System.out.println("0. Exit");
        System.out.print("Choose: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> viewAccounts();
            case "2" -> createAccount();
            case "3" -> deposit();
            case "4" -> withdraw();
            case "5" -> transfer();
            case "6" -> viewHistory();
            case "7" -> categoriesMenu();
            case "8" -> budgetsMenu();
            case "9" -> viewInsights();
            case "10" -> savingGoalsMenu();
            case "11" -> viewDashboard();
            case "12" -> {
                authService.logout();
                System.out.println("Logged out.");
            }
            case "0" -> {
                return false;
            }
            default -> System.out.println("Invalid option.");
        }
        return true;
    }

    // =====================================================
    // ACCOUNTS
    // =====================================================
    private static List<Account> viewAccounts() {
        User user = SessionManager.getCurrentUser();
        List<Account> accounts = accountService.getAccountsForUser(user);
        System.out.println("--- Your Accounts ---");
        for (int i = 0; i < accounts.size(); i++) {
            Account a = accounts.get(i);
            System.out.printf("[%d] %s | %s | %s | Balance: %s%n",
                    i + 1, a.getAccountNumber(), a.getAccountType(), a.getCurrency(), a.getBalance());
        }
        if (accounts.isEmpty()) System.out.println("(no accounts)");
        return accounts;
    }

    private static Account selectAccount(String prompt) {
        List<Account> accounts = viewAccounts();
        if (accounts.isEmpty()) return null;
        System.out.print(prompt);
        int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
        return accounts.get(idx);
    }

    private static void createAccount() {
        try {
            User user = SessionManager.getCurrentUser();
            System.out.print("Account type (CHECKING/SAVINGS/LOAN): ");
            AccountType type = AccountType.valueOf(scanner.nextLine().trim().toUpperCase());
            System.out.print("Currency (USD/KHR): ");
            Currency currency = Currency.valueOf(scanner.nextLine().trim().toUpperCase());

            Account account = accountService.createAccount(user, type, currency);
            System.out.println("Account created: " + account.getAccountNumber());
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    // =====================================================
    // DEPOSIT / WITHDRAW
    // =====================================================
    private static void deposit() {
        try {
            User user = SessionManager.getCurrentUser();
            Account account = selectAccount("Select account to deposit into (number): ");
            if (account == null) return;

            System.out.print("Amount: ");
            BigDecimal amount = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Description: ");
            String desc = scanner.nextLine().trim();

            Long categoryId = promptOptionalCategory(user);

            Transaction txn = accountService.deposit(account.getAccountId(), amount,
                    account.getCurrency(), desc, categoryId, user);
            System.out.println("Deposit successful. New balance: "
                    + accountService.getBalance(account.getAccountId(), user));
        } catch (RuntimeException e) {
            System.out.println("Deposit failed: " + e.getMessage());
        }
    }

    private static void withdraw() {
        try {
            User user = SessionManager.getCurrentUser();
            Account account = selectAccount("Select account to withdraw from (number): ");
            if (account == null) return;

            System.out.print("Amount: ");
            BigDecimal amount = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Description: ");
            String desc = scanner.nextLine().trim();

            Long categoryId = promptOptionalCategory(user);

            Transaction txn = accountService.withdraw(account.getAccountId(), amount,
                    account.getCurrency(), desc, categoryId, user);
            System.out.println("Withdrawal successful. New balance: "
                    + accountService.getBalance(account.getAccountId(), user));
        } catch (RuntimeException e) {
            System.out.println("Withdrawal failed: " + e.getMessage());
        }
    }

    private static Long promptOptionalCategory(User user) {
        System.out.print("Categorize this transaction? (y/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) return null;

        List<Category> categories = categoryService.getVisibleCategories(user);
        for (int i = 0; i < categories.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + categories.get(i).getName());
        }
        System.out.print("Select category (number): ");
        int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
        return categories.get(idx).getCategoryId();
    }

    // =====================================================
    // TRANSFER
    // =====================================================
    private static void transfer() {
        try {
            User user = SessionManager.getCurrentUser();
            Account sender = selectAccount("Select SENDER account (number): ");
            if (sender == null) return;

            System.out.print("Receiver account number (e.g. DGB-XXXXXXXXX): ");
            String receiverNumber = scanner.nextLine().trim();
            Account receiver = accountRepo.findByAccountNumber(receiverNumber)
                    .orElseThrow(() -> new AccountNotFoundException("Receiver account not found"));

            System.out.print("Amount: ");
            BigDecimal amount = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Description: ");
            String desc = scanner.nextLine().trim();

            Transaction txn = accountService.transfer(sender.getAccountId(), receiver.getAccountId(),
                    amount, sender.getCurrency(), desc, UUID.randomUUID(), user);

            System.out.println("Transfer successful!");
            System.out.println("Sender balance: " + accountService.getBalance(sender.getAccountId(), user));
        } catch (RuntimeException e) {
            System.out.println("Transfer failed: " + e.getMessage());
        }
    }

    // =====================================================
    // HISTORY
    // =====================================================
    private static void viewHistory() {
        try {
            User user = SessionManager.getCurrentUser();
            Account account = selectAccount("Select account (number): ");
            if (account == null) return;

            System.out.print("Filter (ALL/INCOME/OUTCOME): ");
            HistoryFilter filter = HistoryFilter.valueOf(scanner.nextLine().trim().toUpperCase());

            List<TransactionView> history = transactionService.getTransactionHistory(
                    account.getAccountId(), filter, user);

            System.out.println("--- Transaction History (" + filter + ") ---");
            for (TransactionView v : history) {
                Transaction t = v.getTransaction();
                System.out.printf("[%s] %s | %s %s | %s%n",
                        v.getDirection(), t.getTransactionType(), t.getAmount(), t.getCurrency(),
                        t.getTransactionDate());
            }
            if (history.isEmpty()) System.out.println("(no transactions)");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    // =====================================================
    // CATEGORIES
    // =====================================================
    private static void categoriesMenu() {
        User user = SessionManager.getCurrentUser();
        System.out.println("1. View all visible categories");
        System.out.println("2. Create custom category");
        System.out.print("Choose: ");
        String choice = scanner.nextLine().trim();

        if (choice.equals("1")) {
            List<Category> categories = categoryService.getVisibleCategories(user);
            for (Category c : categories) {
                String owner = c.isSystem() ? "SYSTEM" : "MINE";
                System.out.println("  - " + c.getName() + " [" + owner + "]");
            }
        } else if (choice.equals("2")) {
            try {
                System.out.print("Category name: ");
                String name = scanner.nextLine().trim();
                System.out.print("Description: ");
                String desc = scanner.nextLine().trim();
                Category c = categoryService.createCustomCategory(name, desc, user);
                System.out.println("Created: " + c.getName());
            } catch (RuntimeException e) {
                System.out.println("Failed: " + e.getMessage());
            }
        }
    }

    // =====================================================
    // BUDGETS
    // =====================================================
    private static void budgetsMenu() {
        User user = SessionManager.getCurrentUser();
        System.out.println("1. View my budgets (with usage)");
        System.out.println("2. Create new budget");
        System.out.print("Choose: ");
        String choice = scanner.nextLine().trim();

        if (choice.equals("1")) {
            List<BudgetView> views = budgetService.getBudgetsWithUsage(user);
            for (BudgetView v : views) {
                System.out.printf("Category #%d | Limit: %s | Spent: %s | Usage: %s%% | Status: %s%n",
                        v.getBudget().getCategoryId(), v.getBudget().getAmountLimit(),
                        v.getActualSpending(), v.getUsagePercentage(), v.getStatus());
            }
            if (views.isEmpty()) System.out.println("(no budgets)");
        } else if (choice.equals("2")) {
            try {
                List<Category> categories = categoryService.getVisibleCategories(user);
                for (int i = 0; i < categories.size(); i++) {
                    System.out.println("  [" + (i + 1) + "] " + categories.get(i).getName());
                }
                System.out.print("Select category (number): ");
                int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
                Long categoryId = categories.get(idx).getCategoryId();

                System.out.print("Budget amount limit: ");
                BigDecimal limit = new BigDecimal(scanner.nextLine().trim());

                System.out.print("Period (WEEKLY/MONTHLY/YEARLY): ");
                BudgetPeriod period = BudgetPeriod.valueOf(scanner.nextLine().trim().toUpperCase());

                Budget budget = budgetService.createBudget(user, categoryId, limit, period,
                        LocalDate.now().withDayOfMonth(1), LocalDate.now());
                System.out.println("Budget created for category id " + categoryId);
            } catch (RuntimeException e) {
                System.out.println("Failed: " + e.getMessage());
            }
        }
    }

    // =====================================================
    // INSIGHTS
    // =====================================================
    private static void viewInsights() {
        User user = SessionManager.getCurrentUser();
        FinancialInsights insights = insightsService.getCurrentMonthInsights(user);

        System.out.println("--- Financial Insights (This Month) ---");
        System.out.println("Total balance: " + insights.getTotalBalance());
        System.out.println("Total income: " + insights.getTotalIncome());
        System.out.println("Total expenses: " + insights.getTotalExpenses());
        System.out.println("Monthly savings: " + insights.getMonthlySavings());
        System.out.println("Savings rate: " + insights.getSavingsRate() + "%");
        System.out.println("Highest spending category: "
                + insights.getHighestSpendingCategory().orElse("No categorized expenses yet"));
    }

    // =====================================================
    // SAVING GOALS (Phase 15)
    // =====================================================
    private static void savingGoalsMenu() {
        User user = SessionManager.getCurrentUser();
        System.out.println("\n--- SAVING GOALS ---");
        System.out.println("1. View my goals");
        System.out.println("2. Create new goal");
        System.out.println("3. Contribute to a goal");
        System.out.println("4. Cancel a goal");
        System.out.print("Choose: ");
        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1" -> viewSavingGoals(user);
            case "2" -> createSavingGoal(user);
            case "3" -> contributeSavingGoal(user);
            case "4" -> cancelSavingGoal(user);
            default -> System.out.println("Invalid option.");
        }
    }

    // =====================================================
// DASHBOARD (Phase 16)
// =====================================================
    private static void viewDashboard() {
        User user = SessionManager.getCurrentUser();
        FinancialDashboard dashboard = dashboardService.buildDashboard(user);

        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("   FINANCIAL DASHBOARD — " + dashboard.getUser().getFullName());
        System.out.println("╚══════════════════════════════════════╝");

        System.out.println("\n--- Accounts (" + dashboard.getAccounts().size() + ") ---");
        for (Account a : dashboard.getAccounts()) {
            System.out.printf("  %s | %s | %s | Balance: %s%n",
                    a.getAccountNumber(), a.getAccountType(), a.getCurrency(), a.getBalance());
        }
        System.out.println("Total balance (all accounts): " + dashboard.getTotalBalance());

        FinancialInsights insights = dashboard.getInsights();
        System.out.println("\n--- This Month ---");
        System.out.println("Income: " + insights.getTotalIncome());
        System.out.println("Expenses: " + insights.getTotalExpenses());
        System.out.println("Savings: " + insights.getMonthlySavings()
                + " (" + insights.getSavingsRate() + "% rate)");
        System.out.println("Top spending category: "
                + insights.getHighestSpendingCategory().orElse("N/A"));

        System.out.println("\n--- Budgets (" + dashboard.getBudgets().size() + ") ---");
        for (BudgetView b : dashboard.getBudgets()) {
            System.out.printf("  Category #%d | %s / %s (%s%%) | %s%n",
                    b.getBudget().getCategoryId(), b.getActualSpending(),
                    b.getBudget().getAmountLimit(), b.getUsagePercentage(), b.getStatus());
        }
        if (dashboard.getBudgets().isEmpty()) System.out.println("  (no active budgets)");

        System.out.println("\n--- Saving Goals (" + dashboard.getSavingGoals().size() + ") ---");
        for (SavingGoal g : dashboard.getSavingGoals()) {
            BigDecimal progress = savingGoalService.getProgressPercentage(g);
            System.out.printf("  %s | %s / %s (%s%%) | %s%n",
                    g.getName(), g.getCurrentAmount(), g.getTargetAmount(), progress, g.getStatus());
        }
        if (dashboard.getSavingGoals().isEmpty()) System.out.println("  (no saving goals)");

        System.out.println();
    }

    private static List<SavingGoal> viewSavingGoals(User user) {
        List<SavingGoal> goals = savingGoalService.getGoalsForUser(user);
        System.out.println("--- Your Saving Goals ---");
        for (int i = 0; i < goals.size(); i++) {
            SavingGoal g = goals.get(i);
            BigDecimal progress = savingGoalService.getProgressPercentage(g);
            System.out.printf("[%d] %s | %s / %s (%s%%) | Status: %s | Deadline: %s%n",
                    i + 1, g.getName(), g.getCurrentAmount(), g.getTargetAmount(),
                    progress, g.getStatus(), g.getDeadline());
        }
        if (goals.isEmpty()) System.out.println("(no saving goals yet)");
        return goals;
    }

    private static void createSavingGoal(User user) {
        try {
            System.out.print("Goal name: ");
            String name = scanner.nextLine().trim();
            System.out.print("Target amount: ");
            BigDecimal target = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Deadline (YYYY-MM-DD, or leave blank for none): ");
            String deadlineStr = scanner.nextLine().trim();
            LocalDate deadline = deadlineStr.isBlank() ? null : LocalDate.parse(deadlineStr);

            SavingGoal goal = savingGoalService.createGoal(user, name, target, deadline);
            System.out.println("Goal created: " + goal.getName() + " | Target: " + goal.getTargetAmount());
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void contributeSavingGoal(User user) {
        try {
            List<SavingGoal> goals = viewSavingGoals(user);
            if (goals.isEmpty()) return;

            System.out.print("Select goal to contribute to (number): ");
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
            Long goalId = goals.get(idx).getGoalId();

            System.out.print("Contribution amount: ");
            BigDecimal amount = new BigDecimal(scanner.nextLine().trim());

            SavingGoal updated = savingGoalService.contribute(goalId, amount, user);
            BigDecimal progress = savingGoalService.getProgressPercentage(updated);
            System.out.println("New progress: " + updated.getCurrentAmount() + " / "
                    + updated.getTargetAmount() + " (" + progress + "%)");
            System.out.println("Status: " + updated.getStatus());
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void cancelSavingGoal(User user) {
        try {
            List<SavingGoal> goals = viewSavingGoals(user);
            if (goals.isEmpty()) return;

            System.out.print("Select goal to cancel (number): ");
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
            Long goalId = goals.get(idx).getGoalId();

            savingGoalService.cancelGoal(goalId, user);
            System.out.println("Goal cancelled.");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }
}