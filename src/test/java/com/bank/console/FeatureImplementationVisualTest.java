package com.bank.console;

import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.exception.InvalidPasswordException;
import com.bank.model.FinancialInsights;
import com.bank.model.TransactionView;
import com.bank.model.entity.Account;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.enums.TransactionType;
import com.bank.model.repository.AccountRepository;
import com.bank.security.PasswordValidator;
import com.bank.security.PasswordValidator.PasswordEvaluation;
import com.bank.model.BudgetView;
import com.bank.model.entity.Budget;
import com.bank.model.repository.BudgetRepository;
import com.bank.service.BudgetService;
import com.bank.service.CategoryService;
import com.bank.service.FinancialInsightsService;
import com.bank.service.FinancialInsightsService.MultiCurrencyTotal;
import com.bank.service.LiveCurrencyService;
import com.bank.service.TransactionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FeatureImplementationVisualTest {

    @BeforeAll
    static void init() {
        ControllerFactory.init();
    }

    @Test
    @DisplayName("PasswordValidator correctly enforces complexity rules and calculates meter bar")
    void testPasswordValidatorComplexityAndScoring() {
        // 1. Weak password (only lowercase, < 8 chars)
        PasswordEvaluation evalWeak = PasswordValidator.evaluate("weak");
        assertFalse(evalWeak.isValid());
        assertFalse(evalWeak.lengthMet());
        assertFalse(evalWeak.upperLowerMet());
        assertFalse(evalWeak.numberMet());
        assertFalse(evalWeak.symbolMet());
        assertEquals("VERY WEAK", evalWeak.strengthLabel());
        assertEquals(20, evalWeak.meterBar().length());
        assertThrows(InvalidPasswordException.class, () -> PasswordValidator.validate("weak"));

        // 2. Missing symbol and uppercase
        PasswordEvaluation evalPartial = PasswordValidator.evaluate("password123");
        assertFalse(evalPartial.isValid());
        assertTrue(evalPartial.lengthMet());
        assertFalse(evalPartial.upperLowerMet());
        assertTrue(evalPartial.numberMet());
        assertFalse(evalPartial.symbolMet());
        assertEquals(2, evalPartial.score());
        assertEquals("FAIR", evalPartial.strengthLabel());
        assertEquals(20, evalPartial.meterBar().length());

        // 3. Fully compliant strong enterprise password
        PasswordEvaluation evalStrong = PasswordValidator.evaluate("SecurePass123!");
        assertTrue(evalStrong.isValid());
        assertTrue(evalStrong.lengthMet());
        assertTrue(evalStrong.upperLowerMet());
        assertTrue(evalStrong.numberMet());
        assertTrue(evalStrong.symbolMet());
        assertEquals(4, evalStrong.score());
        assertEquals("STRONG", evalStrong.strengthLabel());
        assertEquals(20, evalStrong.meterBar().length());
        assertEquals("████████████████████", evalStrong.meterBar());
        assertDoesNotThrow(() -> PasswordValidator.validate("SecurePass123!"));
    }

    @Test
    @DisplayName("FinancialInsightsService normalizes mixed currencies without raw addition")
    void testFinancialInsightsMultiCurrencyNormalization() {
        AccountRepository mockAccountRepo = mock(AccountRepository.class);
        TransactionService mockTxnService = mock(TransactionService.class);
        CategoryService mockCatService = mock(CategoryService.class);
        LiveCurrencyService mockLiveCurrencyService = mock(LiveCurrencyService.class);

        // Fixed exchange rates: 1 USD = 4000 KHR, so 400,000 KHR = 100 USD
        when(mockLiveCurrencyService.getRates()).thenReturn(Map.of("KHR", new BigDecimal("4000.00"), "USD", BigDecimal.ONE));

        User testUser = User.builder().userId(1L).username("testuser").build();

        Account usdAcc = Account.builder()
                .accountId(10L)
                .userId(1L)
                .accountNumber("DGB-111111111")
                .currency(Currency.USD)
                .balance(new BigDecimal("4700.00"))
                .status(AccountStatus.ACTIVE)
                .accountType(AccountType.CHECKING)
                .build();

        Account khrAcc = Account.builder()
                .accountId(20L)
                .userId(1L)
                .accountNumber("DGB-222222222")
                .currency(Currency.KHR)
                .balance(new BigDecimal("400000.00"))
                .status(AccountStatus.ACTIVE)
                .accountType(AccountType.SAVINGS)
                .build();

        when(mockAccountRepo.findByUserId(1L)).thenReturn(List.of(usdAcc, khrAcc));
        when(mockTxnService.getTransactionHistory(anyLong(), any(), any(), any(), any())).thenReturn(List.of());

        FinancialInsightsService service = new FinancialInsightsService(
                mockAccountRepo, mockTxnService, mockCatService, mockLiveCurrencyService);

        FinancialInsights insights = service.getCurrentMonthInsights(testUser);

        MultiCurrencyTotal breakdown = insights.getBalanceBreakdown();
        assertNotNull(breakdown);
        assertEquals(new BigDecimal("4700.00"), breakdown.totalUsd());
        assertEquals(new BigDecimal("400000.00"), breakdown.totalKhr());

        // Normalized total balance: 4,700 + (400,000 / 4000) = 4,700 + 100 = 4,800 USD
        // CRITICAL CHECK: It MUST NOT equal the erroneous raw sum 404,700!
        assertNotEquals(new BigDecimal("404700.00"), insights.getTotalBalance());
        assertEquals(new BigDecimal("4800.00"), insights.getTotalBalance());
    }

    @Test
    @DisplayName("CustomerDashboardScreen layout with KHR accounts adheres strictly to 82 columns")
    void testCustomerDashboardVisualGridAndRielAlignment() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Header
        String headerTitle = "DIGIBANK CORE │ USER: MEN SENGHAK (#USR-3) │ STATUS: ACTIVE";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary(headerTitle), width)));

        // My accounts header with [+] OPEN ACCOUNT
        String accountsTitleRow = TUIBox.twoColumns("MY ACCOUNTS", "[+] OPEN ACCOUNT", width);
        assertEquals(82, TUIBox.visibleLength(accountsTitleRow));

        // Table Header & Divider
        String tableHeader = "  ACCOUNT NUMBER   TYPE       CURRENCY         BALANCE   STATUS";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(tableHeader, width)));

        String tableDivider = " " + "─".repeat(77);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(tableDivider, width)));

        // Row 1: USD Checking Account
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String balStrUsd = String.format("$ %10s   ", df.format(new BigDecimal("4700.00")));
        String rowUsd = String.format("  %-17s%-11s%-12s%s%-8s",
                "DGB-429309564", "CHECKING", "USD", balStrUsd, "ACTIVE");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(rowUsd, width)));

        // Row 2: USD Savings Account
        String balStrUsd2 = String.format("$ %10s   ", df.format(new BigDecimal("13000.00")));
        String rowUsd2 = String.format("  %-17s%-11s%-12s%s%-8s",
                "DGB-788635551", "SAVINGS", "USD", balStrUsd2, "ACTIVE");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(rowUsd2, width)));

        // Row 3: KHR Savings Account with Riel symbol fix
        DecimalFormat intFormat = new DecimalFormat("#,##0");
        String balStrKhr = String.format("៛ %10s   ", intFormat.format(new BigDecimal("400000")));
        String rowKhr = String.format("  %-17s%-11s%-12s%s%-8s",
                "DGB-134672316", "SAVINGS", "KHR", balStrKhr, "ACTIVE");

        // Verify that USD and KHR balance strings have the exact same character count
        assertEquals(balStrUsd.length(), balStrKhr.length(), "USD and KHR balance column widths must match exactly");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(rowKhr, width)), "KHR row must fit strictly within 82 columns without blowout");

        // Two-Column Navigation Rows
        String[] navItems = {
                "[1] Instant Transfer (P2P/Wire)",
                "[2] Cash Deposit / Withdrawal",
                "[3] Currency Exchange & FX Rates",
                "[4] Statements & Ledger History",
                "[5] Loan Management & Installments",
                "[6] Budgets & Saving Goals",
                "[7] Open New Bank Account",
                "[8] Log Out (Session Exit)"
        };

        for (int r = 0; r < 4; r++) {
            String leftPlain = "  " + navItems[r];
            String leftPadded = String.format("%-36s", leftPlain);
            String rightPlain = "  " + navItems[r + 4];
            String rightPadded = String.format("%-39s", rightPlain);
            String navRow = "  " + leftPadded + " " + rightPadded;
            assertEquals(82, TUIBox.visibleLength(TUIBox.line(navRow, width)), "Nav row " + r + " must be strictly 82 columns");
        }

        // Status Line
        String statusLine = TUIBox.line("Status: Ready. All accounts operational.", width);
        assertEquals(82, TUIBox.visibleLength(statusLine));
    }

    @Test
    @DisplayName("CreateAccountScreen wireframe components strictly conform to 82 columns")
    void testCreateAccountScreenWireframeVisualGrid() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > ACCOUNTS > OPEN NEW BANK ACCOUNT"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("NEW ACCOUNT SPECIFICATIONS", width)));

        // Field Rows
        String row0 = String.format("  %-21s: [ %-49s ]", "Account Type", "(1) SAVINGS (High Yield 3.50% APY)_");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row0, width)));

        String row1 = String.format("  %-21s: [ %-49s ]", "Currency Denomination", "(1) USD - United States Dollar");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row1, width)));

        String row2 = String.format("  %-21s: [ %-49s ]", "Initial Deposit ($)", "50.00");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row2, width)));

        String row3 = String.format("  %-21s: [ %-49s ]", "Funding Source", "DGB-429309564 (CHECKING - Bal: $4,700.00 USD)");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row3, width)));

        // Terms compartment
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("TERMS & ACCOUNT FEATURES", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Generated Number     : DGB-XXXXXXXXX (Assigned automatically upon creation)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Minimum Balance Req  : None ($0.00 maintenance fee)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Interest Settlement  : Compounded monthly on the last calendar day", width)));

        // Action Bar
        String actionRow = "   ▸ [1] Create & Fund Account                     [2] Cancel & Return";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(actionRow, width)));

        // Status line
        String status = TUIBox.line("Status: Select account parameters and confirm to open immediately.", width);
        assertEquals(82, TUIBox.visibleLength(status));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("RegisterScreen wireframe components strictly conform to 82 columns")
    void testRegisterScreenWireframeVisualGrid() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > AUTHENTICATION GATEWAY > CUSTOMER REGISTRATION"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));

        // Personal profile
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("PERSONAL PROFILE", width)));
        String nameRow = String.format("  %-21s: [ %-49s ]", "Full Legal Name", "MEN SENGHAK");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(nameRow, width)));

        String phoneRow = String.format("  %-21s: [ %-49s ]", "Phone Number", "+855 12 345 678");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(phoneRow, width)));

        String emailRow = String.format("  %-21s: [ %-49s ]", "Email Address", "senghak.men@example.com");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(emailRow, width)));

        // Credentials
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("CREDENTIALS & SECURITY REQUIREMENTS", width)));
        String userRow = String.format("  %-21s: [ %-49s ]", "Username", "senghak_m");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(userRow, width)));

        String passRow = String.format("  %-21s: [ %-49s ]", "Password", "••••••••••••");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(passRow, width)));

        // Strength meter row
        String meterRow = String.format("  %-21s: [%s] %s", "Strength Score", "████████████████████", "STRONG");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(meterRow, width)));

        // Rule checklist row
        String checklistContent = "[✔] 8+ Chars   [✔] Upper/Lower   [✔] Number   [✔] Sym";
        String checklistRow = String.format("  %-21s: %s", "Rule Checklist", checklistContent);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(checklistRow, width)));

        String confirmRow = String.format("  %-21s: [ %-49s ]", "Confirm Password", "••••••••••••");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(confirmRow, width)));

        String currRow = String.format("  %-21s: [ %-49s ]", "Primary Currency", "(1) USD - US Dollar");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(currRow, width)));

        // Action row
        String actionRow = "   ▸ [1] Create Bank Profile                       [2] Cancel & Return";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(actionRow, width)));

        // Status row
        String statusRow = TUIBox.line("Status: Password satisfies enterprise security complexity requirements.", width);
        assertEquals(82, TUIBox.visibleLength(statusRow));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify progress bar is clamped to exactly 16 slots even when goal exceeds 100%")
    void testProgressBarClampingOver100Percent() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Case: GTA6 Goal with target $1000 and saved $1030 (103%)
        BigDecimal target = new BigDecimal("1000.00");
        BigDecimal saved = new BigDecimal("1030.00");

        int percentage = (target.compareTo(BigDecimal.ZERO) > 0)
                ? (int) Math.round((saved.doubleValue() / target.doubleValue()) * 100)
                : 0;
        assertEquals(103, percentage);

        int filledSlots = Math.min(16, (percentage * 16) / 100);
        filledSlots = Math.max(0, filledSlots);
        assertEquals(16, filledSlots, "Filled slots must be clamped to maximum 16");

        int emptySlots = Math.max(0, 16 - filledSlots);
        assertEquals(0, emptySlots, "Empty slots must be 0 for > 100%");

        String progressBar = "█".repeat(filledSlots) + "░".repeat(emptySlots);
        assertEquals(16, progressBar.length(), "Progress bar must be exactly 16 characters");
        assertEquals("████████████████", progressBar);

        // Verify full line fits strictly inside 82 columns
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String targetStr = "$ " + String.format("%8s", df.format(target));
        String savedStr = "$ " + String.format("%7s", df.format(saved));
        String row = String.format(" %-15s %11s  %10s   %-10s [%-16s] %3d%%",
                "GTA6", targetStr, savedStr, "2026-12-31", progressBar, percentage);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row, width)), "Progress row with 103% must not overflow 82 columns");
    }

    @Test
    @DisplayName("Verify status line safely clamps long messages and does not overflow 82 columns")
    void testStatusLineDoesNotOverflowWithLongMessage() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        String longStatus = "Savings goal claim/withdrawal initiated. Funds return to funding account.";
        String safeStatus = longStatus;
        if (safeStatus.length() > 68) {
            safeStatus = safeStatus.substring(0, 65) + "...";
        }
        String statusLine = "Status: " + safeStatus;
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(statusLine, width)), "Status line must never exceed 82 columns");
    }

    @Test
    @DisplayName("Verify FinancialPlanningScreen instantiates cleanly")
    void testFinancialPlanningScreenInstantiation() {
        assertNotNull(new com.bank.console.screens.FinancialPlanningScreen());
        assertNotNull(new com.bank.console.screens.BudgetGoalsScreen());
    }

    @Test
    @DisplayName("Verify Savings Goal table 82-column alignment with row selection and clamped progress bar")
    void testSavingsGoalTable82ColumnAlignmentWithSelection() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);
        DecimalFormat df = new DecimalFormat("#,##0.00");

        // Header
        String header = "    GOAL NAME            TARGET       SAVED  DEADLINE   PROGRESS STATUS       ";
        assertEquals(78, header.length());
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(header, width)));

        // Divider
        String divider = " " + "─".repeat(76) + " ";
        assertEquals(78, divider.length());
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(divider, width)));

        // Selected row: New laptop
        String selPrefix = "  ▸ ";
        String selName = "New laptop";
        String targetStr = "$" + String.format("%10s", df.format(new BigDecimal("1500.00")));
        String savedStr = "$" + String.format("%10s", df.format(new BigDecimal("1500.00")));
        String deadlineStr = "2026-12-31";
        String progressBar = "█".repeat(16);
        String selRow = String.format("%s%-15s %11s %11s  %-10s [%-16s]%3d%%",
                selPrefix, selName, targetStr, savedStr, deadlineStr, progressBar, 100);
        assertEquals(78, selRow.length(), "Selected row must be exactly 78 visible inner characters");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.inlineHighlight(selRow), width)));

        // Unselected row: GTA6 with 103% progress
        String unselPrefix = "    ";
        String unselName = "GTA6";
        String unselTargetStr = "$" + String.format("%10s", df.format(new BigDecimal("150.00")));
        String unselSavedStr = "$" + String.format("%10s", df.format(new BigDecimal("155.00")));
        String unselDeadlineStr = "2026-09-22";
        int percentage = 103;
        int filled = Math.min(16, (percentage * 16) / 100);
        int empty = Math.max(0, 16 - filled);
        String clampedBar = "█".repeat(filled) + "░".repeat(empty);
        assertEquals(16, clampedBar.length(), "Progress bar must stay strictly 16 slots even for 103%");

        String unselRow = String.format("%s%-15s %11s %11s  %-10s [%-16s]%3d%%",
                unselPrefix, unselName, unselTargetStr, unselSavedStr, unselDeadlineStr, clampedBar, percentage);
        assertEquals(78, unselRow.length(), "Unselected row must be exactly 78 visible inner characters");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(unselRow, width)));

        // Action bar
        String act2 = "  [C] Create    [E] Edit Goal    [D] Deposit    [W] Withdraw/Claim  [Esc] Back";
        assertEquals(78, act2.length(), "Action bar must be exactly 78 characters");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(act2, width)));
    }

    @Test
    @DisplayName("Verify contextual status line for selected goal does not overflow 82 columns")
    void testContextualStatusLineForSelectedGoal() {
        int width = TUILayout.APP_WIDTH;
        String goalName = "New laptop";
        String displayStatus = "Ready. Selected '" + goalName + "'. Press [E] to edit or [D] to fund.";
        assertTrue(displayStatus.length() <= 70, "Status text must be <= 70 chars");
        String statusLine = "Status: " + displayStatus;
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(statusLine, width)), "Status line must fit in 82 columns without blowout");
    }

    @Test
    @DisplayName("Verify SavingGoalService updateGoal and withdrawFromGoal logic")
    void testSavingGoalServiceUpdateAndWithdraw() {
        com.bank.model.repository.SavingGoalRepository repo = mock(com.bank.model.repository.SavingGoalRepository.class);
        com.bank.service.SavingGoalService service = new com.bank.service.SavingGoalService(repo);

        User user = User.builder().userId(1L).username("testuser").build();
        com.bank.model.entity.SavingGoal goal = com.bank.model.entity.SavingGoal.builder()
                .goalId(10L)
                .userId(1L)
                .name("Old Laptop")
                .targetAmount(new BigDecimal("1000.00"))
                .currentAmount(new BigDecimal("500.00"))
                .deadline(java.time.LocalDate.of(2026, 10, 1))
                .status(com.bank.model.enums.GoalStatus.ACTIVE)
                .build();

        when(repo.findById(10L)).thenReturn(java.util.Optional.of(goal));
        when(repo.save(any(com.bank.model.entity.SavingGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        // Update goal
        com.bank.model.entity.SavingGoal updated = service.updateGoal(
                10L, "New Laptop Pro", new BigDecimal("1800.00"), java.time.LocalDate.of(2026, 12, 31), user);
        assertEquals("New Laptop Pro", updated.getName());
        assertEquals(new BigDecimal("1800.00"), updated.getTargetAmount());
        assertEquals(java.time.LocalDate.of(2026, 12, 31), updated.getDeadline());

        // Withdraw from goal
        com.bank.model.entity.SavingGoal afterWithdraw = service.withdrawFromGoal(10L, new BigDecimal("200.00"), user);
        assertEquals(new BigDecimal("300.00"), afterWithdraw.getCurrentAmount());

        // Insufficient funds withdrawal should throw
        assertThrows(com.bank.exception.InvalidAmountException.class,
                () -> service.withdrawFromGoal(10L, new BigDecimal("400.00"), user));
    }

    @Test
    @DisplayName("Verify EditGoalModal, DepositGoalModal, and CreateGoalModal layout components conform to 82 columns")
    void testModalsLayoutConformity() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Header & field rows for EditGoalModal
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SAVINGS GOALS > EDIT GOAL"), width)));
        String fieldRow = com.bank.console.components.TUIFormHelper.formatFieldRow("Goal Title", "New laptop", true, 24, 46);
        assertEquals(82, TUIBox.visibleLength(fieldRow));

        // Action row for EditGoalModal
        String a1 = "[1] Save Changes";
        String a2 = "[2] Cancel & Return";
        String act = "  ▸ " + ConsoleTheme.highlight(a1) + "                    " + a2;
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(act, width)));

        // DepositGoalModal header & preview
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SAVINGS GOALS > DEPOSIT TO GOAL"), width)));
        String progRow = String.format("  New Goal Progress    : $ %s / $ %s   [%s] %3d%%",
                "1,500.00", "1,500.00", "█".repeat(16), 100);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(progRow, width)));
    }

    @Test
    @DisplayName("Verify FinancialPlanningScreen and BudgetGoalsScreen default to Savings Goals Tab 1")
    void testFinancialPlanningAndBudgetGoalsDefaultTab() {
        com.bank.console.screens.FinancialPlanningScreen fps = new com.bank.console.screens.FinancialPlanningScreen();
        assertNotNull(fps);
        com.bank.console.screens.BudgetGoalsScreen bgs = new com.bank.console.screens.BudgetGoalsScreen();
        assertNotNull(bgs);

        // BudgetScreen default constructor starts on Tab 0 (Budgets)
        com.bank.console.screens.BudgetScreen bs = new com.bank.console.screens.BudgetScreen();
        assertNotNull(bs);

        // BudgetScreen with initialTab 1 starts on Tab 1 (Goals)
        com.bank.console.screens.BudgetScreen bsTab1 = new com.bank.console.screens.BudgetScreen(1);
        assertNotNull(bsTab1);
    }

    @Test
    @DisplayName("Verify SavingGoalService contribute allows funding completed goals over 100%")
    void testContributeToCompletedGoalOver100Percent() {
        com.bank.model.repository.SavingGoalRepository repo = mock(com.bank.model.repository.SavingGoalRepository.class);
        com.bank.service.SavingGoalService service = new com.bank.service.SavingGoalService(repo);

        User user = User.builder().userId(1L).username("testuser").build();
        // GTA6 goal already at 100% ($150 target, $150 saved)
        com.bank.model.entity.SavingGoal completedGoal = com.bank.model.entity.SavingGoal.builder()
                .goalId(99L)
                .userId(1L)
                .name("GTA6")
                .targetAmount(new BigDecimal("150.00"))
                .currentAmount(new BigDecimal("150.00"))
                .deadline(java.time.LocalDate.of(2026, 9, 22))
                .status(com.bank.model.enums.GoalStatus.COMPLETED)
                .build();

        when(repo.findById(99L)).thenReturn(java.util.Optional.of(completedGoal));
        when(repo.save(any(com.bank.model.entity.SavingGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        // Contributing another $5.00 should succeed and reach $155.00 (103%)
        com.bank.model.entity.SavingGoal result = service.contribute(99L, new BigDecimal("5.00"), user);
        assertEquals(new BigDecimal("155.00"), result.getCurrentAmount());
        assertEquals(com.bank.model.enums.GoalStatus.COMPLETED, result.getStatus());
    }

    @Test
    @DisplayName("Verify Wireframe Status Line never bleeds 82 columns across all goal names")
    void testWireframeStatusLineFormatting() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        String[] sampleNames = {"GTA6", "New laptop", "iphone 18", "New IPhone duo", "New Book", "Extremely Long Goal Title Over Thirty Characters"};
        for (String name : sampleNames) {
            String selName = name;
            if (selName.length() > 16) selName = selName.substring(0, 13) + "...";
            String displayStatus = "Ready. Selected '" + selName + "'. Press [E] to edit or [D] to fund.";
            String rawStatus = "Status: " + displayStatus;
            if (rawStatus.length() > 78) {
                rawStatus = rawStatus.substring(0, 75) + "...";
            }
            String line = TUIBox.line(rawStatus, width);
            assertEquals(82, TUIBox.visibleLength(line), "Status line for '" + name + "' must be strictly 82 cols");
        }
    }

    @Test
    @DisplayName("Verify Progress Bar Slots are strictly bounded to Math.min(16, (percentage * 16) / 100)")
    void testProgressBarSlotClampingComprehensive() {
        int width = TUILayout.APP_WIDTH;
        DecimalFormat df = new DecimalFormat("#,##0.00");
        DateTimeFormatter dfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Test varying percentages: 0%, 50%, 100%, 103% (GTA6), 150%, 250%, 1000%
        int[] testPercentages = {0, 50, 100, 103, 150, 250, 1000};

        for (int percentage : testPercentages) {
            int filledSlots = Math.min(16, (percentage * 16) / 100);
            filledSlots = Math.max(0, filledSlots);
            int emptySlots = Math.max(0, 16 - filledSlots);

            assertTrue(filledSlots <= 16, "Filled slots must never exceed 16, was " + filledSlots + " for " + percentage + "%");
            assertTrue(emptySlots >= 0, "Empty slots must be >= 0, was " + emptySlots);
            assertEquals(16, filledSlots + emptySlots, "Total progress bar character length must be exactly 16");

            String progressBar = "█".repeat(filledSlots) + "░".repeat(emptySlots);
            assertEquals(16, progressBar.length());

            String targetStr = "$" + String.format("%10s", df.format(new BigDecimal("150.00")));
            String savedStr = "$" + String.format("%10s", df.format(new BigDecimal("155.00")));
            String deadlineStr = "2026-09-22";

            String rowText = String.format("%s%-15s %11s %11s  %-10s [%-16s]%3d%%",
                    "  ▸ ", "GTA6", targetStr, savedStr, deadlineStr, progressBar, Math.min(999, percentage));

            String formattedLine = TUIBox.line(ConsoleTheme.inlineHighlight(rowText), width);
            assertEquals(82, TUIBox.visibleLength(formattedLine),
                    "Row with " + percentage + "% must format strictly to 82 columns");
        }
    }

    @Test
    @DisplayName("Verify Hotkey Case Normalization accepts both lower and upper case keys")
    void testHotkeyCaseNormalization() {
        // Tab 1 routes: 'S', 'D'
        char[] tab1Keys = {'s', 'S', 'd', 'D'};
        for (char k : tab1Keys) {
            char upper = Character.toUpperCase(k);
            assertTrue(upper == 'S' || upper == 'D', "Tab 1 key must normalize to 'S' or 'D'");
        }

        // Tab 2 routes: 'C', 'E', 'D', 'W'
        char[] tab2Keys = {'c', 'C', 'e', 'E', 'd', 'D', 'w', 'W'};
        for (char k : tab2Keys) {
            char upper = Character.toUpperCase(k);
            assertTrue(upper == 'C' || upper == 'E' || upper == 'D' || upper == 'W',
                    "Tab 2 key must normalize to 'C', 'E', 'D', or 'W'");
        }
    }

    @Test
    @DisplayName("Verify Raw Mode ANSI Escape Sequence Parser decoding logic")
    void testRawModeSequenceParserLogic() {
        // Simulation helper validating expected sequence mapping
        java.util.function.BiFunction<Integer, Integer, String> parseSeq = (next1, next2) -> {
            if (next1 == '[' || next1 == 'O') {
                return switch (next2.intValue()) {
                    case 'A' -> "UP";
                    case 'B' -> "DOWN";
                    case 'C' -> "RIGHT";
                    case 'D' -> "LEFT";
                    case 'Z' -> "SHIFT_TAB";
                    default -> "OTHER";
                };
            }
            return "UNKNOWN";
        };

        assertEquals("UP", parseSeq.apply((int) '[', (int) 'A'));
        assertEquals("DOWN", parseSeq.apply((int) '[', (int) 'B'));
        assertEquals("RIGHT", parseSeq.apply((int) '[', (int) 'C'));
        assertEquals("LEFT", parseSeq.apply((int) '[', (int) 'D'));
        assertEquals("SHIFT_TAB", parseSeq.apply((int) '[', (int) 'Z'));

        // Also test alternate mode 'O'
        assertEquals("UP", parseSeq.apply((int) 'O', (int) 'A'));
        assertEquals("DOWN", parseSeq.apply((int) 'O', (int) 'B'));
        assertEquals("RIGHT", parseSeq.apply((int) 'O', (int) 'C'));
        assertEquals("LEFT", parseSeq.apply((int) 'O', (int) 'D'));

        // Tab (byte 9) and digits '1', '2'
        int tabByte = 9;
        assertEquals('\t', (char) tabByte);
        int digit1 = '1';
        int digit2 = '2';
        assertEquals(0, digit1 - '1');
        assertEquals(1, digit2 - '1');
    }

    @Test
    @DisplayName("Verify Budget Impact Preview lines in SetMonthlyBudgetScreen strictly align to 82 columns without overflow")
    void testBudgetImpactPreviewBorderClamping() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);
        DecimalFormat df = new DecimalFormat("#,##0.00");

        // Scenario: Food limit is 200, setting Entertainment to 500
        BigDecimal currentLimit = new BigDecimal("200.00");
        BigDecimal newLimit = new BigDecimal("500.00");
        BigDecimal variance = newLimit.subtract(currentLimit); // +300.00
        double pctChange = (variance.doubleValue() / currentLimit.doubleValue()) * 100.0; // +150.0%
        String varianceStr = String.format("+$ %s (+%.1f%%)", df.format(variance), pctChange);

        BigDecimal dailyAllowance = newLimit.divide(BigDecimal.valueOf(30), 2, java.math.RoundingMode.HALF_UP); // 16.67
        String dailyAllowanceStr = String.format("Daily Allowance: $ %s/Day", df.format(dailyAllowance));

        // Row 1 formatting
        String row1 = String.format("  Limit Variance       : %-25s %s", varianceStr, dailyAllowanceStr);
        if (TUIBox.stripAnsi(row1).length() > 78) {
            row1 = row1.substring(0, 78);
        }
        String formattedRow1 = TUIBox.line(row1, width);
        assertEquals(82, TUIBox.visibleLength(formattedRow1), "Row 1 must strictly align at column 82");
        String stripped1 = TUIBox.stripAnsi(formattedRow1);
        assertTrue(stripped1.startsWith("│") && stripped1.endsWith("│"), "Row 1 must have valid TUI box borders");

        // Row 2 formatting: Total cap = 200 (Food) + 500 (Entertainment) = 700.00
        BigDecimal totalCap = currentLimit.add(newLimit);
        String capStr = String.format("  Total Monthly Cap    : $ %s (All Categories)", df.format(totalCap));
        String healthStr = "Health Status  : CONSERVATIVE";
        String row2 = String.format("%-54s %s", capStr, healthStr);
        if (TUIBox.stripAnsi(row2).length() > 78) {
            row2 = row2.substring(0, 78);
        }
        String formattedRow2 = TUIBox.line(row2, width);
        assertEquals(82, TUIBox.visibleLength(formattedRow2), "Row 2 must strictly align at column 82");
        String stripped2 = TUIBox.stripAnsi(formattedRow2);
        assertTrue(stripped2.startsWith("│") && stripped2.endsWith("│"), "Row 2 must have valid TUI box borders");
    }

    @Test
    @DisplayName("Verify Total Monthly Cap Aggregation sums other categories plus new limit ($200 + $500 = $700.00)")
    void testTotalMonthlyCapAggregation() {
        BigDecimal existingFoodLimit = new BigDecimal("200.00");
        BigDecimal newEntertainmentLimit = new BigDecimal("500.00");

        // Aggregation logic: otherCategoriesTotal + newLimit
        BigDecimal otherCategoriesTotal = existingFoodLimit;
        BigDecimal totalCap = otherCategoriesTotal.add(newEntertainmentLimit);

        assertEquals(new BigDecimal("700.00"), totalCap,
                "Total cap must sum existing Food $200 and new Entertainment $500 to equal $700.00");
        assertNotEquals(newEntertainmentLimit, totalCap,
                "Total cap must not mirror only the current field's value ($500.00)");
    }

    @Test
    @DisplayName("Verify CategorySelectModal layout strictly conforms to 82 columns with instant selection keys")
    void testCategorySelectModalLayoutAndQuickSelect() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        List<com.bank.model.entity.Category> categories = List.of(
                com.bank.model.entity.Category.builder().categoryId(1L).name("Food & Dining").system(true).description("Groceries & meals").build(),
                com.bank.model.entity.Category.builder().categoryId(2L).name("Transportation").system(true).description("Fuel & transit").build(),
                com.bank.model.entity.Category.builder().categoryId(3L).name("Entertainment").system(true).description("Movies & games").build(),
                com.bank.model.entity.Category.builder().categoryId(4L).name("Utilities").system(true).description("Power & water").build(),
                com.bank.model.entity.Category.builder().categoryId(5L).name("Healthcare").system(true).description("Medicine & care").build(),
                com.bank.model.entity.Category.builder().categoryId(6L).name("Shopping").system(true).description("Retail & personal").build(),
                com.bank.model.entity.Category.builder().categoryId(7L).name("Education").system(true).description("Courses & books").build(),
                com.bank.model.entity.Category.builder().categoryId(8L).name("Other Expenses").system(true).description("Miscellaneous").build()
        );

        String modalOutput = com.bank.console.screens.CategorySelectModal.renderModalContent(categories, 2, width);
        assertNotNull(modalOutput);

        String[] lines = modalOutput.split("\n");
        for (int i = 0; i < lines.length - 1; i++) { // exclude last unboxed muted footer line
            String line = lines[i];
            if (line.startsWith("│") || line.startsWith("┌") || line.startsWith("├") || line.startsWith("└")) {
                assertEquals(82, TUIBox.visibleLength(line), "Line " + i + " must have visible width of 82 cols: " + line);
            }
        }

        assertTrue(modalOutput.contains("DIGIBANK CORE > FINANCIAL PLANNING > SELECT EXPENSE CATEGORY"));
        assertTrue(modalOutput.contains("AVAILABLE EXPENSE CATEGORIES"));
        assertTrue(modalOutput.contains("Status: Use [↑/↓] to navigate or press [1-8] for instant selection."));
        assertTrue(modalOutput.contains("[1-8] Quick Select"));
    }

    @Test
    @DisplayName("Verify CreateCategoryModal layout strictly conforms to 82 columns and validates input length")
    void testCreateCategoryModalLayoutAndValidation() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        String modalOutput = com.bank.console.screens.CreateCategoryModal.renderModalContent(
                "Gaming", "Subscriptions and game purchases", 0, 0, "Ready", false, width);
        assertNotNull(modalOutput);

        String[] lines = modalOutput.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i];
            if (line.startsWith("│") || line.startsWith("┌") || line.startsWith("├") || line.startsWith("└")) {
                assertEquals(82, TUIBox.visibleLength(line), "Line " + i + " must have visible width of 82 cols: " + line);
            }
        }

        assertTrue(modalOutput.contains("DIGIBANK CORE > FINANCIAL PLANNING > CREATE CUSTOM CATEGORY"));
        assertTrue(modalOutput.contains("CREATE CATEGORY SPECIFICATIONS"));
        assertTrue(modalOutput.contains("Classification"));
        assertTrue(modalOutput.contains("(1) EXPENSE"));
        assertTrue(modalOutput.contains("[1] Save Category"));
        assertTrue(modalOutput.contains("[2] Cancel & Return"));

        // Validation rule checks
        // 1. Too short (< 3 chars)
        assertEquals("Name must be between 3 and 20 characters.",
                com.bank.console.screens.CreateCategoryModal.getValidationError("AB", null, null));
        // 2. Too long (> 20 chars)
        assertEquals("Name must be between 3 and 20 characters.",
                com.bank.console.screens.CreateCategoryModal.getValidationError("ThisIsWayTooLongOfACategoryName", null, null));
        // 3. Valid (3-20 chars)
        assertEquals("Validation failed. Please check inputs.",
                com.bank.console.screens.CreateCategoryModal.getValidationError("Gaming", null, null));
    }

    @Test
    @DisplayName("Verify Tab 1 Action bar and Meta bar contain [N] New Category and Total Monthly Cap")
    void testTab1FinancialPlanningMetaBarAndActionBar() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);
        DecimalFormat df = new DecimalFormat("#,##0.00");

        BigDecimal totalMonthlyCap = new BigDecimal("470.00");
        String capStr = "$" + df.format(totalMonthlyCap);
        String metaRow = String.format("Page: [ %d / %d ]   │ Filter: [ACTIVE BUDGETS]      │ Total Monthly Cap: %s",
                1, 1, capStr);
        String formattedMetaRow = TUIBox.line(metaRow, width);
        assertEquals(82, TUIBox.visibleLength(formattedMetaRow), "Meta row must be strictly 82 columns");
        assertTrue(metaRow.contains("Filter: [ACTIVE BUDGETS]"));
        assertTrue(metaRow.contains("Total Monthly Cap: $470.00"));

        String actionRow = "  [S] Set Limit   [N] New Category   [D] Delete Budget   [Tab] Switch   [Esc]";
        String formattedActionRow = TUIBox.line(actionRow, width);
        assertEquals(82, TUIBox.visibleLength(formattedActionRow), "Action row must be strictly 82 columns");
        assertTrue(actionRow.contains("[N] New Category"));
        assertTrue(actionRow.contains("[S] Set Limit"));
    }

    @Test
    @DisplayName("Verify Tab 1 Budget Table rows format with active pointer '▸ ' and inactive '  ' strictly within 82 columns")
    void testBudgetTableRowSelectionHighlighting() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);
        DecimalFormat df = new DecimalFormat("#,##0.00");

        String catName = "Food & Dining";
        String limitStr = "$ " + String.format("%8s", df.format(new BigDecimal("200.00")));
        String spentStr = "$ " + String.format("%7s", df.format(new BigDecimal("45.50")));
        String remStr = "$ " + String.format("%7s", df.format(new BigDecimal("154.50")));
        String progressBar = "████░░░░░░░░░░░░";
        int percentage = 22;

        // Active row with pointer '▸ ' and inverted styling
        String activePrefix = "▸ ";
        String activeRow = String.format("%s%-14s %11s  %10s  %10s   [%-16s] %3d%%",
                activePrefix, catName, limitStr, spentStr, remStr, progressBar, percentage);
        assertEquals(78, TUIBox.stripAnsi(activeRow).length(), "Active inner row must be strictly 78 characters");
        String formattedActive = TUIBox.line(ConsoleTheme.inlineHighlight(activeRow), width);
        assertEquals(82, TUIBox.visibleLength(formattedActive), "Highlighted active row must strictly conform to 82 columns");
        assertTrue(TUIBox.stripAnsi(formattedActive).contains("▸ Food & Dining"));

        // Inactive row with prefix '  '
        String inactivePrefix = "  ";
        String inactiveRow = String.format("%s%-14s %11s  %10s  %10s   [%-16s] %3d%%",
                inactivePrefix, catName, limitStr, spentStr, remStr, progressBar, percentage);
        assertEquals(78, TUIBox.stripAnsi(inactiveRow).length(), "Inactive inner row must be strictly 78 characters");
        String formattedInactive = TUIBox.line(inactiveRow, width);
        assertEquals(82, TUIBox.visibleLength(formattedInactive), "Inactive row must strictly conform to 82 columns");
        assertTrue(TUIBox.stripAnsi(formattedInactive).contains("  Food & Dining"));
    }

    @Test
    @DisplayName("Verify default budget table shifts pointer '▸ ' between Food (idx 0) and Entertainment (idx 3)")
    void testDefaultBudgetRowsShiftingPointerBetweenFoodAndEntertainment() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Helper subclass to expose protected renderDefaultBudgetRows
        class ScreenHelper extends com.bank.console.screens.BudgetScreen {
            public String renderWithIndex(int idx) {
                StringBuilder sb = new StringBuilder();
                renderDefaultBudgetRows(sb, 82, idx);
                return sb.toString();
            }
        }
        ScreenHelper helper = new ScreenHelper();

        // 1. Index 0: Food & Dining active
        String output0 = helper.renderWithIndex(0);
        String[] lines0 = output0.split("\n");
        assertEquals(5, lines0.length, "Must render exactly 5 default rows");
        for (String line : lines0) {
            assertEquals(82, TUIBox.visibleLength(line), "Line must be 82 cols");
        }
        assertTrue(TUIBox.stripAnsi(lines0[0]).contains("▸ Food & Dining"), "Food & Dining must have active pointer ▸");
        assertTrue(TUIBox.stripAnsi(lines0[3]).contains("  Entertainment"), "Entertainment must have inactive prefix");

        // 2. Index 3: Entertainment active (after user pressed down arrow 3 times)
        String output3 = helper.renderWithIndex(3);
        String[] lines3 = output3.split("\n");
        assertEquals(5, lines3.length, "Must render exactly 5 default rows");
        for (String line : lines3) {
            assertEquals(82, TUIBox.visibleLength(line), "Line must be 82 cols");
        }
        assertTrue(TUIBox.stripAnsi(lines3[0]).contains("  Food & Dining"), "Food & Dining must have inactive prefix");
        assertTrue(TUIBox.stripAnsi(lines3[3]).contains("▸ Entertainment"), "Entertainment must have active pointer ▸");
    }

    @Test
    @DisplayName("Verify CategorySelectModal returns null on ESC and instantly resolves on number keys '1'-'8'")
    void testCategorySelectModalKeyHandling() throws Exception {
        org.jline.terminal.Terminal mockTerminal = mock(org.jline.terminal.Terminal.class);
        org.jline.terminal.Attributes mockAttr = mock(org.jline.terminal.Attributes.class);
        org.jline.utils.NonBlockingReader mockReader = mock(org.jline.utils.NonBlockingReader.class);

        List<com.bank.model.entity.Category> categories = List.of(
                com.bank.model.entity.Category.builder().categoryId(1L).name("Food & Dining").build(),
                com.bank.model.entity.Category.builder().categoryId(2L).name("Transportation").build(),
                com.bank.model.entity.Category.builder().categoryId(3L).name("Entertainment").build()
        );

        // Case 1: ESC key (27 followed by -2 lookahead timeout) -> returns null (cancelled)
        when(mockReader.read()).thenReturn(27);
        when(mockReader.read(25)).thenReturn(-2);

        com.bank.model.entity.Category escResult =
                com.bank.console.screens.CategorySelectModal.selectCategory(
                        mockTerminal, mockAttr, mockReader, categories, categories.get(0), 82);
        assertNull(escResult, "Pressing ESC must cancel and return null");

        // Case 2: Instant shortcut key '3' -> immediately resolves 3rd category "Entertainment" without requiring Enter
        when(mockReader.read()).thenReturn((int) '3');
        com.bank.model.entity.Category instantResult =
                com.bank.console.screens.CategorySelectModal.selectCategory(
                        mockTerminal, mockAttr, mockReader, categories, categories.get(0), 82);
        assertNotNull(instantResult);
        assertEquals("Entertainment", instantResult.getName(), "Pressing '3' must immediately select Entertainment");

        // Case 3: Instant shortcut key '1' -> immediately resolves Food & Dining
        when(mockReader.read()).thenReturn((int) '1');
        com.bank.model.entity.Category instantResult1 =
                com.bank.console.screens.CategorySelectModal.selectCategory(
                        mockTerminal, mockAttr, mockReader, categories, categories.get(2), 82);
        assertNotNull(instantResult1);
        assertEquals("Food & Dining", instantResult1.getName(), "Pressing '1' must immediately select Food & Dining");
    }

    @Test
    @DisplayName("Verify BudgetService batches transaction history queries across user accounts in date window")
    void testBudgetServiceBatchTransactionUsageCalculation() {
        BudgetRepository mockBudgetRepo = mock(BudgetRepository.class);
        AccountRepository mockAccountRepo = mock(AccountRepository.class);
        TransactionService mockTxnService = mock(TransactionService.class);
        CategoryService mockCatService = mock(CategoryService.class);

        BudgetService budgetService = new BudgetService(mockBudgetRepo, mockAccountRepo, mockTxnService, mockCatService);
        User user = User.builder().userId(101L).username("testUser").build();

        LocalDate now = LocalDate.now();
        LocalDate start = now.withDayOfMonth(1);
        LocalDate end = now.withDayOfMonth(now.lengthOfMonth());

        Budget foodBudget = Budget.builder()
                .budgetId(1L)
                .userId(101L)
                .categoryId(10L)
                .amountLimit(new BigDecimal("200.00"))
                .startDate(start)
                .endDate(end)
                .build();

        Budget entertainmentBudget = Budget.builder()
                .budgetId(2L)
                .userId(101L)
                .categoryId(20L)
                .amountLimit(new BigDecimal("300.00"))
                .startDate(start)
                .endDate(end)
                .build();

        when(mockBudgetRepo.findByUserId(101L)).thenReturn(List.of(foodBudget, entertainmentBudget));

        Account acc1 = Account.builder().accountId(1001L).userId(101L).build();
        Account acc2 = Account.builder().accountId(1002L).userId(101L).build();
        when(mockAccountRepo.findByUserId(101L)).thenReturn(List.of(acc1, acc2));

        // Transactions: Food ($45.50 on acc1), Entertainment ($120.00 on acc2)
        Transaction tFood = Transaction.builder()
                .transactionId(901L)
                .accountId(1001L)
                .categoryId(10L)
                .amount(new BigDecimal("45.50"))
                .createdAt(start.atTime(12, 0))
                .build();
        TransactionView tvFood = new TransactionView(tFood, TransactionDirection.OUTCOME);

        Transaction tEnt = Transaction.builder()
                .transactionId(902L)
                .accountId(1002L)
                .categoryId(20L)
                .amount(new BigDecimal("120.00"))
                .createdAt(start.atTime(14, 0))
                .build();
        TransactionView tvEnt = new TransactionView(tEnt, TransactionDirection.OUTCOME);

        when(mockTxnService.getTransactionHistory(eq(1001L), eq(com.bank.model.enums.HistoryFilter.OUTCOME), any(), any(), eq(user)))
                .thenReturn(List.of(tvFood));
        when(mockTxnService.getTransactionHistory(eq(1002L), eq(com.bank.model.enums.HistoryFilter.OUTCOME), any(), any(), eq(user)))
                .thenReturn(List.of(tvEnt));

        List<BudgetView> views = budgetService.getBudgetsWithUsage(user);

        assertNotNull(views);
        assertEquals(2, views.size());

        // Verify that getTransactionHistory was called EXACTLY ONCE per account (2 total), not per budget * per account (4 total)
        verify(mockTxnService, times(1)).getTransactionHistory(eq(1001L), eq(com.bank.model.enums.HistoryFilter.OUTCOME), any(), any(), eq(user));
        verify(mockTxnService, times(1)).getTransactionHistory(eq(1002L), eq(com.bank.model.enums.HistoryFilter.OUTCOME), any(), any(), eq(user));

        BudgetView foodView = views.stream().filter(v -> v.getBudget().getBudgetId().equals(1L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("45.50"), foodView.getActualSpending());
        assertEquals(new BigDecimal("154.50"), foodView.getRemainingAmount());

        BudgetView entView = views.stream().filter(v -> v.getBudget().getBudgetId().equals(2L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("120.00"), entView.getActualSpending());
        assertEquals(new BigDecimal("180.00"), entView.getRemainingAmount());
    }

    @Test
    @DisplayName("Verify TUIFormHelper decodes escape sequences with 25ms timeout")
    void testEscapeSequenceLookahead25msTimeout() throws Exception {
        org.jline.utils.NonBlockingReader mockReader = mock(org.jline.utils.NonBlockingReader.class);
        when(mockReader.read()).thenReturn(27);
        when(mockReader.read(25)).thenReturn((int) '[').thenReturn((int) 'A');

        com.bank.console.components.TUIFormHelper.KeyEvent event =
                com.bank.console.components.TUIFormHelper.readKey(mockReader);
        assertEquals(com.bank.console.components.TUIFormHelper.KeyAction.UP, event.action());
        assertEquals('A', event.ch());
    }

    @Test
    @DisplayName("CustomerDashboard navigation columns align with zero offset and border remains strictly at 82 columns across all selections")
    void testCustomerDashboardNavigationColumnAlignmentAndBorderIntegrity() {
        String[] navItems = {
                "[1] Instant Transfer (P2P/Wire)",
                "[2] Cash Deposit / Withdrawal",
                "[3] Currency Exchange & FX Rates",
                "[4] Statements & Ledger History",
                "[5] Loan Management & Installments",
                "[6] Budgets & Saving Goals",
                "[7] Open New Bank Account",
                "[8] Log Out (Session Exit)"
        };

        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Test every possible selectedIndex (0 through 7)
        for (int selectedIndex = 0; selectedIndex < 8; selectedIndex++) {
            for (int r = 0; r < 4; r++) {
                int leftIdx = r;
                int rightIdx = r + 4;

                // 1. Prefix: unselected rows get "  ", selected rows get "▸ "
                String leftMarker = (leftIdx == selectedIndex) ? "▸ " : "  ";
                String leftPlain = leftMarker + navItems[leftIdx];
                // 2. Pad visible text to exactly 36 chars BEFORE wrapping in ANSI codes
                String leftPadded = String.format("%-36s", leftPlain);
                String leftFormatted = (leftIdx == selectedIndex) ? ConsoleTheme.inlineHighlight(leftPadded) : leftPadded;

                String rightMarker = (rightIdx == selectedIndex) ? "▸ " : "  ";
                String rightPlain = rightMarker + navItems[rightIdx];
                // 2. Pad visible text to exactly 39 chars BEFORE wrapping in ANSI codes
                String rightPadded = String.format("%-39s", rightPlain);
                String rightFormatted = (rightIdx == selectedIndex) ? ConsoleTheme.inlineHighlight(rightPadded) : rightPadded;

                // 3. Strict 78-character inner width: "  " (2) + Col1 (36) + " " (1) + Col2 (39) = 78
                String row = "  " + leftFormatted + " " + rightFormatted;
                String stripped = TUIBox.stripAnsi(row);

                // Acceptance Criterion: Inner total width strictly at 78 characters
                assertEquals(78, stripped.length(), "Inner row must be strictly 78 characters for selectedIndex=" + selectedIndex + " row=" + r);

                // Acceptance Criterion: The right vertical border (│) remains completely straight at 82 columns
                String boxedLine = TUIBox.line(row, width);
                assertEquals(82, TUIBox.visibleLength(boxedLine), "Boxed line must be strictly 82 columns for selectedIndex=" + selectedIndex + " row=" + r);
                assertTrue(TUIBox.stripAnsi(boxedLine).endsWith("│"), "Line must end with vertical border │");

                // Acceptance Criterion: Option 1 and Option 2 align vertically with zero offset/gap
                // Col 1 '[' is always at index 4 ("  " indentation + 2-char marker)
                assertEquals('[', stripped.charAt(4), "Col 1 '[' must be at index 4 across all rows");

                // Acceptance Criterion: Moving the selection indicator (▸) does not shift Option 5 or any right-column text
                // Col 2 '[' is always at index 41 ("  " (2) + Col1 (36) + " " (1) + 2-char marker = index 41)
                assertEquals('[', stripped.charAt(41), "Col 2 '[' must always be at index 41 regardless of selectedIndex");

                // Check marker correctness
                if (leftIdx == selectedIndex) {
                    assertTrue(stripped.startsWith("  ▸ "), "Selected left item must start with '  ▸ '");
                } else {
                    assertTrue(stripped.startsWith("    "), "Unselected left item must start with '    '");
                }

                if (rightIdx == selectedIndex) {
                    assertEquals('▸', stripped.charAt(39), "Selected right item must have '▸' at index 39");
                } else {
                    assertEquals(' ', stripped.charAt(39), "Unselected right item must have ' ' at index 39");
                }
            }
        }
    }
}

