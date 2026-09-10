package com.bank.console;

import com.bank.controller.*;
import com.bank.model.repository.*;
import com.bank.report.StatementReportService;
import com.bank.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight Dependency Container (IoC / DI).
 * Manages singleton instances of Repositories, Services, and Controllers without Spring.
 * Guarantees that the entire dependency graph is constructed once in strict topological order.
 */
public final class ControllerFactory {
    private static final Logger logger = LoggerFactory.getLogger(ControllerFactory.class);

    // Repositories (11)
    private static UserRepository userRepository;
    private static AccountRepository accountRepository;
    private static AdminRepository adminRepository;
    private static AuditLogRepository auditLogRepository;
    private static BudgetRepository budgetRepository;
    private static CategoryRepository categoryRepository;
    private static FraudAlertRepository fraudAlertRepository;
    private static LoanRepository loanRepository;
    private static LoanPaymentRepository loanPaymentRepository;
    private static SavingGoalRepository savingGoalRepository;
    private static TransactionRepository transactionRepository;

    // Services (19)
    private static AuditLogService auditLogService;
    private static FraudDetectionService fraudDetectionService;
    private static AccountService accountService;
    private static AuthenticationService authenticationService;
    private static AuthService authService;
    private static AdminAuthService adminAuthService;
    private static TransactionService transactionService;
    private static CategoryService categoryService;
    private static BudgetService budgetService;
    private static SavingGoalService savingGoalService;
    private static FinancialInsightsService financialInsightsService;
    private static DashboardService dashboardService;
    private static RiskAssessmentService riskAssessmentService;
    private static LoanService loanService;
    private static LoanApprovalService loanApprovalService;
    private static LoanRepaymentService loanRepaymentService;
    private static CurrencyExchangeService currencyExchangeService;
    private static AdminService adminService;
    private static FraudInvestigationService fraudInvestigationService;
    private static StatementReportService statementReportService;

    // Controllers (10)
    private static AuthController authController;
    private static AccountController accountController;
    private static TransactionController transactionController;
    private static FinancialController financialController;
    private static BudgetController budgetController;
    private static CategoryController categoryController;
    private static LoanController loanController;
    private static ReportController reportController;
    private static SavingGoalController savingGoalController;
    private static AdminController adminController;

    private static boolean initialized = false;

    private ControllerFactory() {}

    /**
     * Initializes the full dependency graph once.
     * Safe to call multiple times (idempotent).
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }

        logger.info("Initializing ControllerFactory: Wiring Repositories -> Services -> Controllers...");

        // 1. Repositories
        userRepository = new UserRepositoryImpl();
        accountRepository = new AccountRepositoryImpl();
        adminRepository = new AdminRepositoryImpl();
        auditLogRepository = new AuditLogRepositoryImpl();
        budgetRepository = new BudgetRepositoryImpl();
        categoryRepository = new CategoryRepositoryImpl();
        fraudAlertRepository = new FraudAlertRepositoryImpl();
        loanRepository = new LoanRepositoryImpl();
        loanPaymentRepository = new LoanPaymentRepositoryImpl();
        savingGoalRepository = new SavingGoalRepositoryImpl();
        transactionRepository = new TransactionRepositoryImpl();

        // 2. Services
        auditLogService = new AuditLogService(auditLogRepository);
        fraudDetectionService = new FraudDetectionService(fraudAlertRepository, transactionRepository);
        accountService = new AccountService(accountRepository, transactionRepository, fraudDetectionService);
        authenticationService = new AuthenticationService(userRepository, adminRepository, accountService, auditLogService);
        authService = new AuthService(userRepository, accountService);
        adminAuthService = new AdminAuthService(adminRepository, auditLogService);
        transactionService = new TransactionService(transactionRepository, accountRepository);
        categoryService = new CategoryService(categoryRepository);
        budgetService = new BudgetService(budgetRepository, accountRepository, transactionService, categoryService);
        savingGoalService = new SavingGoalService(savingGoalRepository);
        financialInsightsService = new FinancialInsightsService(accountRepository, transactionService, categoryService);
        dashboardService = new DashboardService(accountRepository, financialInsightsService, budgetService, savingGoalService);
        riskAssessmentService = new RiskAssessmentService();
        loanService = new LoanService(loanRepository, riskAssessmentService);
        loanApprovalService = new LoanApprovalService(loanRepository, accountRepository, transactionRepository, auditLogService);
        loanRepaymentService = new LoanRepaymentService(loanRepository, loanPaymentRepository, accountRepository, transactionRepository);
        currencyExchangeService = new CurrencyExchangeService(accountRepository, transactionRepository);
        adminService = new AdminService(adminRepository, userRepository, accountRepository, transactionRepository, fraudAlertRepository, auditLogService);
        fraudInvestigationService = new FraudInvestigationService(fraudAlertRepository, accountRepository, auditLogRepository, auditLogService);
        statementReportService = new StatementReportService(transactionRepository);

        // 3. Controllers
        authController = new AuthController(authenticationService, authService, adminAuthService);
        accountController = new AccountController(accountService, currencyExchangeService, accountRepository);
        transactionController = new TransactionController(transactionService);
        financialController = new FinancialController(financialInsightsService, dashboardService);
        budgetController = new BudgetController(budgetService);
        categoryController = new CategoryController(categoryService);
        loanController = new LoanController(loanService, loanApprovalService, loanRepaymentService);
        reportController = new ReportController(statementReportService);
        savingGoalController = new SavingGoalController(savingGoalService);
        adminController = new AdminController(adminService, auditLogService, fraudInvestigationService);

        initialized = true;
        logger.info("ControllerFactory initialized successfully. All 10 controllers ready.");
    }

    private static void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }

    // ==========================================
    // Controller Accessors
    // ==========================================

    public static synchronized AuthController getAuthController() {
        ensureInitialized();
        return authController;
    }

    public static synchronized AccountController getAccountController() {
        ensureInitialized();
        return accountController;
    }

    public static synchronized TransactionController getTransactionController() {
        ensureInitialized();
        return transactionController;
    }

    public static synchronized FinancialController getFinancialController() {
        ensureInitialized();
        return financialController;
    }

    public static synchronized BudgetController getBudgetController() {
        ensureInitialized();
        return budgetController;
    }

    public static synchronized CategoryController getCategoryController() {
        ensureInitialized();
        return categoryController;
    }

    public static synchronized LoanController getLoanController() {
        ensureInitialized();
        return loanController;
    }

    public static synchronized ReportController getReportController() {
        ensureInitialized();
        return reportController;
    }

    public static synchronized SavingGoalController getSavingGoalController() {
        ensureInitialized();
        return savingGoalController;
    }

    public static synchronized AdminController getAdminController() {
        ensureInitialized();
        return adminController;
    }

    // ==========================================
    // Service Accessors (for advanced TUI needs)
    // ==========================================

    public static synchronized AccountService getAccountService() {
        ensureInitialized();
        return accountService;
    }

    public static synchronized AuthenticationService getAuthenticationService() {
        ensureInitialized();
        return authenticationService;
    }

    public static synchronized AuthService getAuthService() {
        ensureInitialized();
        return authService;
    }

    public static synchronized AdminAuthService getAdminAuthService() {
        ensureInitialized();
        return adminAuthService;
    }

    public static synchronized TransactionService getTransactionService() {
        ensureInitialized();
        return transactionService;
    }

    public static synchronized FinancialInsightsService getFinancialInsightsService() {
        ensureInitialized();
        return financialInsightsService;
    }

    public static synchronized DashboardService getDashboardService() {
        ensureInitialized();
        return dashboardService;
    }

    public static synchronized BudgetService getBudgetService() {
        ensureInitialized();
        return budgetService;
    }

    public static synchronized SavingGoalService getSavingGoalService() {
        ensureInitialized();
        return savingGoalService;
    }

    public static synchronized LoanService getLoanService() {
        ensureInitialized();
        return loanService;
    }

    public static synchronized LoanApprovalService getLoanApprovalService() {
        ensureInitialized();
        return loanApprovalService;
    }

    public static synchronized LoanRepaymentService getLoanRepaymentService() {
        ensureInitialized();
        return loanRepaymentService;
    }

    public static synchronized AdminService getAdminService() {
        ensureInitialized();
        return adminService;
    }

    public static synchronized AuditLogService getAuditLogService() {
        ensureInitialized();
        return auditLogService;
    }

    public static synchronized FraudInvestigationService getFraudInvestigationService() {
        ensureInitialized();
        return fraudInvestigationService;
    }

    public static synchronized StatementReportService getStatementReportService() {
        ensureInitialized();
        return statementReportService;
    }

    public static synchronized UserRepository getUserRepository() {
        ensureInitialized();
        return userRepository;
    }

    public static synchronized AccountRepository getAccountRepository() {
        ensureInitialized();
        return accountRepository;
    }

    public static synchronized LoanRepository getLoanRepository() {
        ensureInitialized();
        return loanRepository;
    }
}