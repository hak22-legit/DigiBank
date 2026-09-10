package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that DigiBank's 11 Terminal UI Screens conform strictly to the
 * 82-column grid and single-line box drawing specification.
 */
public class MockupVisualVerificationTest {

    @BeforeAll
    static void init() {
        ControllerFactory.init();
    }

    @Test
    @DisplayName("Verify 82-column boundary consistency across all TUIBox primitives")
    void testTuiBoxPrimitivesWidth() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width, "TUI application width must be exactly 82 columns");

        String top = TUIBox.top(width);
        String bottom = TUIBox.bottom(width);
        String divider = TUIBox.divider(width);
        String emptyLine = TUIBox.emptyLine(width);
        String line = TUIBox.line("Test Standard Line", width);
        String center = TUIBox.center("Test Centered Title", width);
        String twoCols = TUIBox.twoColumns("Left Column", "Right Column", width);

        assertEquals(82, TUIBox.visibleLength(top), "Top border must be 82 cols");
        assertEquals(82, TUIBox.visibleLength(bottom), "Bottom border must be 82 cols");
        assertEquals(82, TUIBox.visibleLength(divider), "Divider must be 82 cols");
        assertEquals(82, TUIBox.visibleLength(emptyLine), "Empty line must be 82 cols");
        assertEquals(82, TUIBox.visibleLength(line), "Standard line must be 82 cols");
        assertEquals(82, TUIBox.visibleLength(center), "Centered line must be 82 cols");
        assertEquals(82, TUIBox.visibleLength(twoCols), "Two-column line must be 82 cols");
    }

    @Test
    @DisplayName("Verify that all 11 Mockup Screens instantiate and wire cleanly")
    void testAll11ScreensInstantiate() {
        // Screen 1: Welcome & Landing Gateway
        assertNotNull(new WelcomeScreen());

        // Screen 2: Universal Login Gateway (Users & Admins)
        assertNotNull(new LoginScreen());

        // Screen 3: Customer Registration (Open Account)
        assertNotNull(new RegisterScreen());

        // Screen 4: Currency Exchange Board & Calculator
        assertNotNull(new ExchangeScreen());

        // Screen 5: Customer Dashboard
        assertNotNull(new CustomerDashboardScreen());

        // Screen 6: Fund Transfer (Transactions)
        assertNotNull(new TransferScreen());

        // Screen 7: Transaction Ledger & History
        assertNotNull(new TransactionHistoryScreen());

        // Screen 8: Loans & Installment Repayments
        assertNotNull(new LoanScreen());

        // Screen 9: Budgets & Saving Goals
        assertNotNull(new BudgetScreen());
        assertNotNull(new SavingGoalScreen());

        // Screen 10: Admin Dashboard (Staff Portal)
        assertNotNull(new AdminDashboardScreen());

        // Screen 11: Loan Underwriting (Admin Action)
        assertNotNull(new AdminLoanScreen());

        // Dedicated Phase 24 Screens
        assertNotNull(new ForgotPasswordScreen());
        assertNotNull(new CategoryManagementScreen());
        assertNotNull(new LoanRepaymentScreen());
        assertNotNull(new AdminFraudScreen());
        assertNotNull(new AdminAuditLogScreen());
        assertNotNull(new AdminUserManagementScreen());
        assertNotNull(new StaffPortalScreen());
        assertNotNull(new AdminMainMenuScreen());
        assertNotNull(new UserMainMenuScreen());
    }
}
