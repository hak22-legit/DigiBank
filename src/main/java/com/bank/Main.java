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
    private static final FraudAlertRepository fraudAlertRepo = new FraudAlertRepositoryImpl();

    // Services (order matters: dependencies must be declared before the
    // services that need them, since these are static fields initialized top-to-bottom)
    private static final FraudDetectionService fraudDetectionService =
            new FraudDetectionService(fraudAlertRepo, transactionRepo);
    private static final AccountService accountService =
            new AccountService(accountRepo, transactionRepo, fraudDetectionService);
    private static final AuthService authService = new AuthService(userRepo, accountService);
    private static final TransactionService transactionService = new TransactionService(transactionRepo, accountRepo);
    private static final CategoryService categoryService = new CategoryService(categoryRepo);
    private static final BudgetService budgetService = new BudgetService(budgetRepo, accountRepo, transactionService, categoryService);
    private static final FinancialInsightsService insightsService = new FinancialInsightsService(accountRepo, transactionService, categoryService);
    private static final SavingGoalService savingGoalService = new SavingGoalService(savingGoalRepo);
    private static final DashboardService dashboardService = new DashboardService(accountRepo, insightsService, budgetService, savingGoalService);

    private static final AuditLogRepository auditLogRepo = new AuditLogRepositoryImpl();
    private static final AuditLogService auditLogService = new AuditLogService(auditLogRepo);
    private static final AdminRepository adminRepo = new AdminRepositoryImpl();
    private static final AdminAuthService adminAuthService = new AdminAuthService(adminRepo, auditLogService);
    private static final AdminService adminService = new AdminService(
            adminRepo, userRepo, accountRepo, transactionRepo, fraudAlertRepo, auditLogService);
    private static final FraudInvestigationService fraudInvestigationService =
            new FraudInvestigationService(fraudAlertRepo, accountRepo, auditLogRepo, auditLogService);
    private static final LoanRepository loanRepo = new LoanRepositoryImpl();
    private static final LoanService loanService = new LoanService(loanRepo);
    private static final LoanApprovalService loanApprovalService =
            new LoanApprovalService(loanRepo, accountRepo, transactionRepo, auditLogService);


    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   DIGIBANK - Interactive Test Console");
        System.out.println("========================================");

        boolean running = true;
        while (running) {
            if (SessionManager.isAdminLoggedIn()) {
                running = showAdminMenu();
            } else if (SessionManager.isUserLoggedIn()) {
                running = showMainMenu();
            } else {
                running = showAuthMenu();
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
        System.out.println("3. Admin Login");
        System.out.println("4. Forgot Admin Password");
        System.out.println("0. Exit");
        System.out.print("Choose: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> register();
            case "2" -> login();
            case "3" -> adminLogin();
            case "4" -> forgotAdminPassword();
            case "0" -> {
                return false;
            }
            default -> System.out.println("Invalid option.");
        }
        return true;
    }

    private static void adminLogin() {
        try {
            System.out.print("Admin username: ");
            String username = scanner.nextLine().trim();
            System.out.print("Admin password: ");
            String password = scanner.nextLine().trim();

            Admin admin = adminAuthService.login(username, password);
            System.out.println("Welcome, " + admin.getFullName() + " (" + admin.getRole() + ")");
        } catch (RuntimeException e) {
            System.out.println("Admin login failed: " + e.getMessage());
        }
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

    private static void forgotAdminPassword() {
        try {
            System.out.print("Admin username: ");
            String username = scanner.nextLine().trim();

            String question = adminAuthService.getSecurityQuestion(username);
            System.out.println("Security question: " + question);
            System.out.print("Your answer: ");
            String answer = scanner.nextLine().trim();

            System.out.print("New password: ");
            String newPassword = scanner.nextLine().trim();

            adminAuthService.recoverPasswordWithSecurityAnswer(username, answer, newPassword);
            System.out.println("Password recovered successfully! You can now log in.");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
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
        System.out.println("12. View my fraud alerts");
        System.out.println("13. Apply for a loan");
        System.out.println("14. My loans");
        System.out.println("15. Logout");
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
            case "12" -> viewFraudAlerts();
            case "13" -> applyForLoanMenu();
            case "14" -> viewMyLoans();
            case "15" -> {
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

    // =====================================================
    // FRAUD ALERTS (Phase 17)
    // =====================================================
    private static void viewFraudAlerts() {
        User user = SessionManager.getCurrentUser();
        List<FraudAlert> alerts = fraudAlertRepo.findByUserId(user.getUserId());

        System.out.println("--- Your Fraud Alerts ---");
        for (FraudAlert a : alerts) {
            System.out.printf("[%s] %s | Status: %s | Created: %s%n",
                    a.getRiskLevel(), a.getDescription(), a.getStatus(), a.getCreatedAt());
        }
        if (alerts.isEmpty()) System.out.println("(no fraud alerts - nothing suspicious detected)");
    }

    // =====================================================
// ADMIN MENU (Phase 18-19)
// =====================================================
    private static boolean showAdminMenu() {
        Admin admin = SessionManager.getCurrentAdmin();
        System.out.println("\n--- ADMIN MENU (" + admin.getFullName() + " | " + admin.getRole() + ") ---");

        if (admin.getRole() == AdminRole.SUPER_ADMIN) {
            System.out.println("1. Set/update my security question");
            System.out.println("2. Create new admin");
            System.out.println("3. Reset another admin's password");
            System.out.println("4. View all admins");
            System.out.println("5. Suspend an admin");
            System.out.println("6. Reactivate an admin");
            System.out.println("7. View all users");
            System.out.println("8. View system statistics");
            System.out.println("9. View all fraud alerts (oversight)");
            System.out.println("10. View audit logs (paginated)");
        } else {
            System.out.println("1. Change my password");
        }
        if (admin.getRole() == AdminRole.COMPLIANCE_OFFICER) {
            System.out.println("11. View open fraud alerts");
            System.out.println("12. Investigate a fraud alert");
            System.out.println("13. Resolve a fraud alert");
            System.out.println("14. Freeze an account");
            System.out.println("15. Unfreeze an account");
            System.out.println("16. View fraud-related audit logs");
        }
        if (admin.getRole() == AdminRole.LOAN_OFFICER) {
            System.out.println("18. View pending loans");
            System.out.println("19. Approve a loan");
            System.out.println("20. Reject a loan");
        }
        System.out.println("17. Logout");
        System.out.println("0. Exit");
        System.out.print("Choose: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> {
                if (admin.getRole() == AdminRole.SUPER_ADMIN) setSecurityQuestionMenu(admin);
                else changeAdminPassword(admin);
            }
            case "2" -> createAdminMenu(admin);
            case "3" -> resetAdminPasswordMenu(admin);
            case "4" -> viewAllAdmins(admin);
            case "5" -> suspendAdminMenu(admin);
            case "6" -> reactivateAdminMenu(admin);
            case "7" -> viewAllUsers(admin);
            case "8" -> viewSystemStats(admin);
            case "9" -> viewAllFraudAlertsOversight(admin);
            case "10" -> viewAuditLogsPaginated(admin);
            case "11" -> viewOpenFraudAlerts(admin);
            case "12" -> investigateFraudMenu(admin);
            case "13" -> resolveFraudMenu(admin);
            case "14" -> freezeAccountMenu(admin);
            case "15" -> unfreezeAccountMenu(admin);
            case "16" -> viewFraudAuditLogs(admin);
            case "17" -> {
                adminAuthService.logout();
                System.out.println("Logged out.");
            }
            case "18" -> viewPendingLoans(admin);
            case "19" -> approveLoanMenu(admin);
            case "20" -> rejectLoanMenu(admin);
            case "0" -> {
                return false;
            }
            default -> System.out.println("Invalid option.");
        }
        return true;
    }

    private static void viewPendingLoans(Admin admin) {
        try {
            List<Loan> loans = loanApprovalService.getPendingLoans(admin);
            System.out.println("--- Pending Loans ---");
            for (Loan l : loans) {
                System.out.printf("[id=%d] user=%d | Requested: %s | Risk: %s (%s) | Income: %s | Credit: %d%n",
                        l.getLoanId(), l.getUserId(), l.getRequestedAmount(),
                        l.getRiskLevel(), l.getRiskScore(), l.getMonthlyIncome(), l.getCreditScore());
            }
            if (loans.isEmpty()) System.out.println("(no pending loans)");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void approveLoanMenu(Admin admin) {
        try {
            System.out.print("Loan ID to approve: ");
            Long loanId = Long.parseLong(scanner.nextLine().trim());
            System.out.print("Borrower's account number for disbursement (e.g. DGB-XXXXXXXXX): ");
            String accountNumber = scanner.nextLine().trim();
            Account account = accountRepo.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));

            System.out.print("Approved amount: ");
            BigDecimal approvedAmount = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Interest rate (%): ");
            BigDecimal rate = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Term (months): ");
            Integer term = Integer.parseInt(scanner.nextLine().trim());

            Loan approved = loanApprovalService.approveLoan(admin, loanId, account.getAccountId(),
                    approvedAmount, rate, term);
            System.out.println("Loan approved and disbursed! Status: " + approved.getStatus());
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void rejectLoanMenu(Admin admin) {
        try {
            System.out.print("Loan ID to reject: ");
            Long loanId = Long.parseLong(scanner.nextLine().trim());
            System.out.print("Rejection reason: ");
            String reason = scanner.nextLine().trim();

            Loan rejected = loanApprovalService.rejectLoan(admin, loanId, reason);
            System.out.println("Loan rejected. Status: " + rejected.getStatus());
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }    

    private static void changeAdminPassword(Admin admin) {
        try {
            System.out.print("Current password: ");
            String currentPassword = scanner.nextLine().trim();
            System.out.print("New password: ");
            String newPassword = scanner.nextLine().trim();
            System.out.print("Confirm new password: ");
            String confirmPassword = scanner.nextLine().trim();

            if (!newPassword.equals(confirmPassword)) {
                System.out.println("Passwords do not match.");
                return;
            }

            adminAuthService.changePassword(admin, currentPassword, newPassword);
            System.out.println("Password changed successfully!");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void createAdminMenu(Admin admin) {
        try {
            System.out.print("New admin username: ");
            String username = scanner.nextLine().trim();
            System.out.print("New admin email: ");
            String email = scanner.nextLine().trim();
            System.out.print("New admin password: ");
            String password = scanner.nextLine().trim();
            System.out.print("Full name: ");
            String fullName = scanner.nextLine().trim();
            System.out.print("Role (LOAN_OFFICER/COMPLIANCE_OFFICER/SUPER_ADMIN): ");
            AdminRole role = AdminRole.valueOf(scanner.nextLine().trim().toUpperCase());

            Admin newAdmin = adminService.createAdmin(admin, username, email, password, fullName, role);
            System.out.println("Admin created: " + newAdmin.getUsername() + " (" + newAdmin.getRole() + ")");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void resetAdminPasswordMenu(Admin admin) {
        try {
            List<Admin> allAdmins = adminService.getAllAdmins(admin);
            for (int i = 0; i < allAdmins.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + allAdmins.get(i).getUsername()
                        + " (" + allAdmins.get(i).getRole() + ")");
            }
            System.out.print("Select admin (number): ");
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
            Long targetId = allAdmins.get(idx).getAdminId();

            System.out.print("New password: ");
            String newPassword = scanner.nextLine().trim();

            adminService.resetAdminPassword(admin, targetId, newPassword);
            System.out.println("Password reset successfully.");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void viewOpenFraudAlerts(Admin admin) {
        try {
            List<FraudAlert> alerts = fraudInvestigationService.getOpenAlerts(admin);
            System.out.println("--- Open Fraud Alerts ---");
            for (FraudAlert a : alerts) {
                System.out.printf("[id=%d] [%s] user=%d | %s%n",
                        a.getAlertId(), a.getRiskLevel(), a.getUserId(), a.getDescription());
            }
            if (alerts.isEmpty()) System.out.println("(no open alerts)");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void investigateFraudMenu(Admin admin) {
        try {
            System.out.print("Alert ID to investigate: ");
            Long alertId = Long.parseLong(scanner.nextLine().trim());
            FraudAlert updated = fraudInvestigationService.investigateAlert(admin, alertId);
            System.out.println("Alert status now: " + updated.getStatus());
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void resolveFraudMenu(Admin admin) {
        try {
            System.out.print("Alert ID to resolve: ");
            Long alertId = Long.parseLong(scanner.nextLine().trim());
            System.out.print("Resolution notes: ");
            String notes = scanner.nextLine().trim();
            System.out.print("Confirmed fraud? (y/n): ");
            boolean confirmed = scanner.nextLine().trim().equalsIgnoreCase("y");

            FraudAlert updated = fraudInvestigationService.resolveAlert(admin, alertId, notes, confirmed);
            System.out.println("Alert resolved as: " + updated.getStatus());
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void freezeAccountMenu(Admin admin) {
        try {
            System.out.print("Account number to freeze (e.g. DGB-XXXXXXXXX): ");
            String accountNumber = scanner.nextLine().trim();
            Account account = accountRepo.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));

            System.out.print("Reason: ");
            String reason = scanner.nextLine().trim();

            Account updated = fraudInvestigationService.freezeAccount(admin, account.getAccountId(), reason);
            System.out.println("Account " + updated.getAccountNumber() + " is now " + updated.getStatus());
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void unfreezeAccountMenu(Admin admin) {
        try {
            System.out.print("Account number to unfreeze (e.g. DGB-XXXXXXXXX): ");
            String accountNumber = scanner.nextLine().trim();
            Account account = accountRepo.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));

            Account updated = fraudInvestigationService.unfreezeAccount(admin, account.getAccountId());
            System.out.println("Account " + updated.getAccountNumber() + " is now " + updated.getStatus());
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void viewFraudAuditLogs(Admin admin) {
        List<AuditLog> logs = auditLogService.getFraudRelatedLogs(admin);
        System.out.println("--- Fraud-Related Audit Logs ---");
        for (AuditLog log : logs) {
            System.out.printf("[%s] admin=%s | %s | %s%n",
                    log.getCreatedAt(), log.getAdminId(), log.getAction(), log.getDetails());
        }
        if (logs.isEmpty()) System.out.println("(no fraud-related logs yet)");
    }

    private static void setSecurityQuestionMenu(Admin admin) {
        try {
            System.out.print("Security question: ");
            String question = scanner.nextLine().trim();
            System.out.print("Answer: ");
            String answer = scanner.nextLine().trim();

            adminAuthService.setSecurityQuestion(admin, question, answer);
            System.out.println("Security question saved.");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void viewAllAdmins(Admin admin) {
        try {
            List<Admin> admins = adminService.getAllAdmins(admin);
            System.out.println("--- All Admins ---");
            for (Admin a : admins) {
                System.out.printf("[id=%d] %s | %s | %s | Status: %s%n",
                        a.getAdminId(), a.getUsername(), a.getFullName(), a.getRole(), a.getStatus());
            }
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void suspendAdminMenu(Admin admin) {
        try {
            List<Admin> admins = adminService.getAllAdmins(admin);
            for (int i = 0; i < admins.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + admins.get(i).getUsername()
                        + " (" + admins.get(i).getRole() + ", " + admins.get(i).getStatus() + ")");
            }
            System.out.print("Select admin to suspend (number): ");
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;

            adminService.suspendAdmin(admin, admins.get(idx).getAdminId());
            System.out.println("Admin suspended.");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void reactivateAdminMenu(Admin admin) {
        try {
            List<Admin> admins = adminService.getAllAdmins(admin);
            for (int i = 0; i < admins.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + admins.get(i).getUsername()
                        + " (" + admins.get(i).getRole() + ", " + admins.get(i).getStatus() + ")");
            }
            System.out.print("Select admin to reactivate (number): ");
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;

            adminService.reactivateAdmin(admin, admins.get(idx).getAdminId());
            System.out.println("Admin reactivated.");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void viewAllUsers(Admin admin) {
        try {
            List<User> users = adminService.getAllUsers(admin);
            System.out.println("--- All Users ---");
            for (User u : users) {
                System.out.printf("[id=%d] %s | %s | %s | Status: %s%n",
                        u.getUserId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getStatus());
            }
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void viewSystemStats(Admin admin) {
        try {
            SystemStats stats = adminService.getSystemStats(admin);
            System.out.println("--- System Statistics ---");
            System.out.println("Total users: " + stats.getTotalUsers());
            System.out.println("Total admins: " + stats.getTotalAdmins());
            System.out.println("Total accounts: " + stats.getTotalAccounts());
            System.out.println("Total transactions: " + stats.getTotalTransactions());
            System.out.println("Total fraud alerts: " + stats.getTotalFraudAlerts()
                    + " (" + stats.getOpenFraudAlerts() + " open)");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void applyForLoanMenu() {
        try {
            User user = SessionManager.getCurrentUser();
            System.out.print("Requested amount: ");
            BigDecimal amount = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Monthly income: ");
            BigDecimal income = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Monthly expense: ");
            BigDecimal expense = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Existing debt: ");
            BigDecimal debt = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Credit score (300-850): ");
            Integer creditScore = Integer.parseInt(scanner.nextLine().trim());
            System.out.print("Term (months): ");
            Integer term = Integer.parseInt(scanner.nextLine().trim());

            Loan loan = loanService.applyForLoan(user, amount, income, expense, debt, creditScore, term);
            System.out.println("Loan application submitted! ID=" + loan.getLoanId()
                    + " | Risk: " + loan.getRiskLevel() + " (" + loan.getRiskScore() + ")"
                    + " | Status: " + loan.getStatus());
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void viewMyLoans() {
        User user = SessionManager.getCurrentUser();
        List<Loan> loans = loanService.getUserLoans(user);
        System.out.println("--- My Loans ---");
        for (Loan l : loans) {
            System.out.printf("[id=%d] Requested: %s | Status: %s | Risk: %s (%s)%n",
                    l.getLoanId(), l.getRequestedAmount(), l.getStatus(), l.getRiskLevel(), l.getRiskScore());
        }
        if (loans.isEmpty()) System.out.println("(no loan applications yet)");
    }

    private static void viewAllFraudAlertsOversight(Admin admin) {
        try {
            List<FraudAlert> alerts = adminService.getAllFraudAlerts(admin);
            System.out.println("--- All Fraud Alerts (Oversight) ---");
            for (FraudAlert a : alerts) {
                System.out.printf("[id=%d] [%s] Status: %s | %s%n",
                        a.getAlertId(), a.getRiskLevel(), a.getStatus(), a.getDescription());
            }
            if (alerts.isEmpty()) System.out.println("(no fraud alerts)");
        } catch (RuntimeException e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private static void viewAuditLogsPaginated(Admin admin) {
        int page = 1;
        int pageSize = 10;
        boolean browsing = true;

        while (browsing) {
            PagedResult<AuditLog> result = auditLogService.getLogsPaginated(admin, page, pageSize);

            System.out.println("--- Audit Logs (Page " + result.getCurrentPage()
                    + " / " + result.getTotalPages() + ", " + result.getTotalItems() + " total) ---");
            for (AuditLog log : result.getItems()) {
                System.out.printf("[%s] admin=%s | %s on %s#%s | %s%n",
                        log.getCreatedAt(), log.getAdminId(), log.getAction(),
                        log.getTargetTable(), log.getTargetId(), log.getDetails());
            }
            if (result.getItems().isEmpty()) System.out.println("(no logs on this page)");

            System.out.println("\n[n] Next page | [p] Previous page | [q] Quit view");
            System.out.print("Choose: ");
            String nav = scanner.nextLine().trim().toLowerCase();

            switch (nav) {
                case "n" -> {
                    if (result.hasNextPage()) page++;
                    else System.out.println("Already on last page.");
                }
                case "p" -> {
                    if (result.hasPreviousPage()) page--;
                    else System.out.println("Already on first page.");
                }
                case "q" -> browsing = false;
                default -> System.out.println("Invalid option.");
            }
        }
    }

}