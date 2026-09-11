package com.bank.console;

import com.bank.console.screens.*;
import com.bank.controller.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControllerFactoryTest {

    @BeforeAll
    static void setUp() {
        ControllerFactory.init();
    }

    @Test
    @DisplayName("Verify all 10 controllers are wired and non-null singletons")
    void testControllersAreNonNullSingletons() {
        AuthController auth1 = ControllerFactory.getAuthController();
        AuthController auth2 = ControllerFactory.getAuthController();
        assertNotNull(auth1, "AuthController should not be null");
        assertSame(auth1, auth2, "AuthController should be a singleton");

        AccountController acc1 = ControllerFactory.getAccountController();
        AccountController acc2 = ControllerFactory.getAccountController();
        assertNotNull(acc1, "AccountController should not be null");
        assertSame(acc1, acc2, "AccountController should be a singleton");

        TransactionController tx1 = ControllerFactory.getTransactionController();
        TransactionController tx2 = ControllerFactory.getTransactionController();
        assertNotNull(tx1, "TransactionController should not be null");
        assertSame(tx1, tx2, "TransactionController should be a singleton");

        FinancialController fin1 = ControllerFactory.getFinancialController();
        FinancialController fin2 = ControllerFactory.getFinancialController();
        assertNotNull(fin1, "FinancialController should not be null");
        assertSame(fin1, fin2, "FinancialController should be a singleton");

        BudgetController bud1 = ControllerFactory.getBudgetController();
        BudgetController bud2 = ControllerFactory.getBudgetController();
        assertNotNull(bud1, "BudgetController should not be null");
        assertSame(bud1, bud2, "BudgetController should be a singleton");

        CategoryController cat1 = ControllerFactory.getCategoryController();
        CategoryController cat2 = ControllerFactory.getCategoryController();
        assertNotNull(cat1, "CategoryController should not be null");
        assertSame(cat1, cat2, "CategoryController should be a singleton");

        LoanController loan1 = ControllerFactory.getLoanController();
        LoanController loan2 = ControllerFactory.getLoanController();
        assertNotNull(loan1, "LoanController should not be null");
        assertSame(loan1, loan2, "LoanController should be a singleton");

        ReportController rep1 = ControllerFactory.getReportController();
        ReportController rep2 = ControllerFactory.getReportController();
        assertNotNull(rep1, "ReportController should not be null");
        assertSame(rep1, rep2, "ReportController should be a singleton");

        SavingGoalController sg1 = ControllerFactory.getSavingGoalController();
        SavingGoalController sg2 = ControllerFactory.getSavingGoalController();
        assertNotNull(sg1, "SavingGoalController should not be null");
        assertSame(sg1, sg2, "SavingGoalController should be a singleton");

        AdminController adm1 = ControllerFactory.getAdminController();
        AdminController adm2 = ControllerFactory.getAdminController();
        assertNotNull(adm1, "AdminController should not be null");
        assertSame(adm1, adm2, "AdminController should be a singleton");
    }

    @Test
    @DisplayName("Verify screens instantiate cleanly with default ControllerFactory wiring")
    void testScreensInstantiation() {
        assertDoesNotThrow(() -> new WelcomeScreen());
        assertDoesNotThrow(() -> new RegisterScreen());
        assertDoesNotThrow(() -> new CustomerDashboardScreen());
        assertDoesNotThrow(() -> new LoginScreen());
        assertDoesNotThrow(() -> new LoginScreen(ControllerFactory.getAuthController()));
        assertDoesNotThrow(() -> new UserMainMenuScreen());
        assertDoesNotThrow(() -> new AdminMainMenuScreen());
        assertDoesNotThrow(() -> new UserDashboardScreen());
        assertDoesNotThrow(() -> new AccountScreen());
        assertDoesNotThrow(() -> new DepositScreen());
        assertDoesNotThrow(() -> new WithdrawScreen());
        assertDoesNotThrow(() -> new TransferScreen());
        assertDoesNotThrow(() -> new TransactionHistoryScreen());
        assertDoesNotThrow(() -> new BudgetScreen());
        assertDoesNotThrow(() -> new SavingGoalScreen());
        assertDoesNotThrow(() -> new InsightsScreen());
        assertDoesNotThrow(() -> new LoanScreen());
        assertDoesNotThrow(() -> new StatementScreen());
        assertDoesNotThrow(() -> new StaffPortalScreen());
        assertDoesNotThrow(() -> new AdminDashboardScreen());
        assertDoesNotThrow(() -> new AdminUserManagementScreen());
        assertDoesNotThrow(() -> new AdminLoanScreen());
        assertDoesNotThrow(() -> new AdminFraudScreen());
        assertDoesNotThrow(() -> new AdminAuditLogScreen());
        assertDoesNotThrow(() -> new ExchangeScreen());
    }

    @Test
    @DisplayName("Verify service accessors are available and non-null")
    void testServicesAreAvailable() {
        assertNotNull(ControllerFactory.getAccountService());
        assertNotNull(ControllerFactory.getAuthenticationService());
        assertNotNull(ControllerFactory.getAuthService());
        assertNotNull(ControllerFactory.getAdminAuthService());
        assertNotNull(ControllerFactory.getTransactionService());
        assertNotNull(ControllerFactory.getFinancialInsightsService());
        assertNotNull(ControllerFactory.getDashboardService());
        assertNotNull(ControllerFactory.getBudgetService());
        assertNotNull(ControllerFactory.getSavingGoalService());
        assertNotNull(ControllerFactory.getLoanService());
        assertNotNull(ControllerFactory.getLoanApprovalService());
        assertNotNull(ControllerFactory.getLoanRepaymentService());
        assertNotNull(ControllerFactory.getAdminService());
        assertNotNull(ControllerFactory.getAuditLogService());
        assertNotNull(ControllerFactory.getFraudInvestigationService());
        assertNotNull(ControllerFactory.getStatementReportService());
        assertNotNull(ControllerFactory.getLiveCurrencyService());
        assertNotNull(ControllerFactory.getUserRepository());
        assertNotNull(ControllerFactory.getAccountRepository());
        assertNotNull(ControllerFactory.getLoanRepository());
    }

    @Test
    @DisplayName("Verify TUIBox renders exactly 82 columns for borders and lines")
    void testTUIBox82Columns() {
        int width = com.bank.console.components.TUILayout.APP_WIDTH;
        assertEquals(82, width);
        assertEquals(82, com.bank.console.components.TUIBox.visibleLength(com.bank.console.components.TUIBox.top(width)));
        assertEquals(82, com.bank.console.components.TUIBox.visibleLength(com.bank.console.components.TUIBox.bottom(width)));
        assertEquals(82, com.bank.console.components.TUIBox.visibleLength(com.bank.console.components.TUIBox.divider(width)));
        assertEquals(82, com.bank.console.components.TUIBox.visibleLength(com.bank.console.components.TUIBox.emptyLine(width)));
        assertEquals(82, com.bank.console.components.TUIBox.visibleLength(com.bank.console.components.TUIBox.line("Test Line Content", width)));
        assertEquals(82, com.bank.console.components.TUIBox.visibleLength(com.bank.console.components.TUIBox.center("Centered Title", width)));
    }

    @Test
    @DisplayName("Verify TuiTable, TuiComponents, and TerminalContext functions")
    void testTuiComponentsAndTable() {
        String meter = com.bank.console.components.TuiComponents.getProgressBar(50, 100, 10);
        assertNotNull(meter);
        assertTrue(meter.contains("50.0%"));

        com.bank.console.components.TuiTable table = new com.bank.console.components.TuiTable()
                .addColumn("ID", false)
                .addColumn("Name", false)
                .addRow("1", "Savings Account");
        String rendered = table.renderToString();
        assertNotNull(rendered);
        assertTrue(rendered.contains("Savings Account"));

        TerminalContext context = TerminalContext.getInstance();
        assertNotNull(context);
        assertNotNull(context.getTerminal());
        assertNotNull(context.getLineReader());
        assertEquals(82, com.bank.console.components.TuiComponents.APP_WIDTH);
    }
}
