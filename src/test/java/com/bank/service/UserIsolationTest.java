package com.bank.service;

import com.bank.model.BudgetView;
import com.bank.model.TransactionView;
import com.bank.model.dto.LoanDTO;
import com.bank.model.entity.*;
import com.bank.model.enums.*;
import com.bank.model.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserIsolationTest {

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private RiskAssessmentService riskAssessmentService;
    @Mock
    private LoanPaymentRepository loanPaymentRepository;

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionService transactionService;
    @Mock
    private CategoryService categoryService;

    @Mock
    private SavingGoalRepository savingGoalRepository;

    private LoanService loanService;
    private BudgetService budgetService;
    private SavingGoalService savingGoalService;

    private User senghak;
    private User demo;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(loanRepository, riskAssessmentService, loanPaymentRepository);
        budgetService = new BudgetService(budgetRepository, accountRepository, transactionService, categoryService);
        savingGoalService = new SavingGoalService(savingGoalRepository);

        senghak = User.builder().userId(1L).username("senghak").build();
        demo = User.builder().userId(2L).username("demo").build();
    }

    @Test
    @DisplayName("Task 1: Loan Service queries strictly by user_id and does not call findAll()")
    void testLoanServiceUserIsolation() {
        Loan senghakLoan = Loan.builder()
                .loanId(101L)
                .userId(senghak.getUserId())
                .requestedAmount(new BigDecimal("5000.00"))
                .approvedAmount(new BigDecimal("5000.00"))
                .outstandingBalance(new BigDecimal("4200.00"))
                .status(LoanStatus.ACTIVE)
                .build();

        when(loanRepository.findByUserId(senghak.getUserId())).thenReturn(List.of(senghakLoan));
        when(loanRepository.findByUserId(demo.getUserId())).thenReturn(List.of());

        // Senghak sees their loans
        List<LoanDTO> senghakLoans = loanService.getUserLoans(senghak);
        assertEquals(1, senghakLoans.size());
        assertEquals(101L, senghakLoans.get(0).getLoanId());

        // Demo user has zero loans and receives empty list (no leakage)
        List<LoanDTO> demoLoans = loanService.getUserLoans(demo);
        assertNotNull(demoLoans);
        assertTrue(demoLoans.isEmpty());

        // Overload using userId / currentUser.getId()
        List<LoanDTO> demoLoansById = loanService.getUserLoans(demo.getId());
        assertNotNull(demoLoansById);
        assertTrue(demoLoansById.isEmpty());

        // Confirm findAll() is never invoked in customer portal loan queries
        verify(loanRepository, never()).findAll();
        verify(loanRepository, times(1)).findByUserId(senghak.getUserId());
        verify(loanRepository, times(2)).findByUserId(demo.getUserId());
    }

    @Test
    @DisplayName("Task 2: Budget Service queries active limits strictly by user_id")
    void testBudgetServiceUserIsolation() {
        Budget senghakBudget = Budget.builder()
                .budgetId(201L)
                .userId(senghak.getUserId())
                .categoryId(10L)
                .amountLimit(new BigDecimal("300.00"))
                .period(BudgetPeriod.MONTHLY)
                .status(BudgetStatus.ACTIVE)
                .startDate(LocalDate.now().withDayOfMonth(1))
                .endDate(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()))
                .build();

        Category foodCategory = Category.builder().categoryId(10L).name("Food & Dining").build();

        when(budgetRepository.findByUserId(senghak.getUserId())).thenReturn(List.of(senghakBudget));
        when(budgetRepository.findByUserId(demo.getUserId())).thenReturn(List.of());
        when(accountRepository.findByUserId(senghak.getUserId())).thenReturn(List.of());

        // Senghak sees their 1 budget
        List<BudgetView> senghakBudgets = budgetService.getBudgetsWithUsage(senghak);
        assertEquals(1, senghakBudgets.size());
        assertEquals(201L, senghakBudgets.get(0).getBudget().getBudgetId());

        // Demo user has 0 budgets and receives empty list (no fallback to mock budgets)
        List<BudgetView> demoBudgets = budgetService.getBudgetsWithUsage(demo);
        assertNotNull(demoBudgets);
        assertTrue(demoBudgets.isEmpty());

        List<Budget> demoBudgetsRaw = budgetService.getBudgetsForUser(demo);
        assertNotNull(demoBudgetsRaw);
        assertTrue(demoBudgetsRaw.isEmpty());

        verify(budgetRepository, times(1)).findByUserId(senghak.getUserId());
        verify(budgetRepository, times(2)).findByUserId(demo.getUserId());
    }

    @Test
    @DisplayName("Task 2: calculateSpentForCategory filters transactions strictly where account.user_id = :userId")
    void testCalculateSpentForCategoryStrictAccountFiltering() {
        Long categoryId = 15L;
        LocalDateTime start = LocalDateTime.now().minusDays(10);
        LocalDateTime end = LocalDateTime.now();

        Account senghakAccount = Account.builder()
                .accountId(501L)
                .userId(senghak.getUserId())
                .accountNumber("DGB-111111111")
                .build();

        Account demoAccount = Account.builder()
                .accountId(502L)
                .userId(demo.getUserId())
                .accountNumber("DGB-222222222")
                .build();

        // Transaction belonging to Senghak in Category 15
        Transaction senghakTx = Transaction.builder()
                .transactionId(901L)
                .accountId(senghakAccount.getAccountId())
                .categoryId(categoryId)
                .amount(new BigDecimal("150.00"))
                .currency(Currency.USD)
                .transactionDate(LocalDateTime.now().minusDays(2))
                .build();

        TransactionView senghakView = new TransactionView(
                senghakTx, TransactionDirection.OUTCOME, new BigDecimal("150.00"), Currency.USD);

        // When querying accounts for Senghak vs Demo
        when(accountRepository.findByUserId(senghak.getUserId())).thenReturn(List.of(senghakAccount));
        when(accountRepository.findByUserId(demo.getUserId())).thenReturn(List.of(demoAccount));

        // When querying transaction history for Senghak vs Demo accounts
        when(transactionService.getTransactionHistory(
                eq(senghakAccount.getAccountId()), eq(HistoryFilter.OUTCOME), eq(start), eq(end), any(User.class)))
                .thenReturn(List.of(senghakView));
        when(transactionService.getTransactionHistory(
                eq(demoAccount.getAccountId()), eq(HistoryFilter.OUTCOME), eq(start), eq(end), any(User.class)))
                .thenReturn(List.of());

        // Senghak spent $150
        BigDecimal senghakSpent = budgetService.calculateSpentForCategory(categoryId, senghak, start, end);
        assertEquals(new BigDecimal("150.00"), senghakSpent);

        // Demo spent $0.00; Senghak's transaction is NOT counted
        BigDecimal demoSpent = budgetService.calculateSpentForCategory(categoryId, demo, start, end);
        assertEquals(BigDecimal.ZERO, demoSpent);

        // Overload using demo.getId()
        BigDecimal demoSpentById = budgetService.calculateSpentForCategory(categoryId, demo.getId(), start, end);
        assertEquals(BigDecimal.ZERO, demoSpentById);
    }

    @Test
    @DisplayName("Task 3: Saving Goals are fetched strictly for current user via findByUserId")
    void testSavingGoalServiceUserIsolation() {
        SavingGoal senghakGoal = SavingGoal.builder()
                .goalId(301L)
                .userId(senghak.getUserId())
                .name("GTA6")
                .targetAmount(new BigDecimal("150.00"))
                .currentAmount(new BigDecimal("155.00"))
                .status(GoalStatus.COMPLETED)
                .build();

        when(savingGoalRepository.findByUserId(senghak.getUserId())).thenReturn(List.of(senghakGoal));
        when(savingGoalRepository.findByUserId(demo.getUserId())).thenReturn(List.of());

        // Senghak sees their saving goal
        List<SavingGoal> senghakGoals = savingGoalService.getGoalsForUser(senghak);
        assertEquals(1, senghakGoals.size());
        assertEquals("GTA6", senghakGoals.get(0).getName());

        // Demo user has 0 goals and receives empty list (no fallback to default goals)
        List<SavingGoal> demoGoals = savingGoalService.getGoalsForUser(demo);
        assertNotNull(demoGoals);
        assertTrue(demoGoals.isEmpty());

        // Overload using userId
        List<SavingGoal> demoGoalsById = savingGoalService.getGoalsForUser(demo.getId());
        assertNotNull(demoGoalsById);
        assertTrue(demoGoalsById.isEmpty());

        verify(savingGoalRepository, never()).findAll();
        verify(savingGoalRepository, times(1)).findByUserId(senghak.getUserId());
        verify(savingGoalRepository, times(2)).findByUserId(demo.getUserId());
    }

    @Test
    @DisplayName("Task 4: Null safety for guest or unauthenticated callers returns empty list")
    void testNullUserSafety() {
        assertNotNull(loanService.getUserLoans((User) null));
        assertTrue(loanService.getUserLoans((User) null).isEmpty());
        assertTrue(loanService.getUserLoans((Long) null).isEmpty());

        assertNotNull(budgetService.getBudgetsWithUsage((User) null));
        assertTrue(budgetService.getBudgetsWithUsage((User) null).isEmpty());
        assertTrue(budgetService.getBudgetsWithUsage((Long) null).isEmpty());
        assertTrue(budgetService.getBudgetsForUser((User) null).isEmpty());
        assertTrue(budgetService.getBudgetsForUser((Long) null).isEmpty());

        assertNotNull(savingGoalService.getGoalsForUser((User) null));
        assertTrue(savingGoalService.getGoalsForUser((User) null).isEmpty());
        assertTrue(savingGoalService.getGoalsForUser((Long) null).isEmpty());
    }

    @Test
    @DisplayName("Task 4: Empty state rendering when user has 0 loans renders guidance without mock loans")
    void testLoanEmptyStateRendering() {
        int width = com.bank.console.components.TUILayout.APP_WIDTH;
        String renderedEmpty = com.bank.console.screens.LoanScreen.renderContent(
                new java.util.ArrayList<>(), 0, 0, 0, null, false, width);

        assertTrue(renderedEmpty.contains("No active records found. Press [3] to apply."));
        assertFalse(renderedEmpty.contains("#LN-13"), "Must not leak mock loan #LN-13");
        assertFalse(renderedEmpty.contains("#LN-20"), "Must not leak mock loan #LN-20");

        String[] lines = renderedEmpty.split("\n");
        assertEquals(27, lines.length, "Empty facility screen height must remain strictly 27 rows");
    }
}
