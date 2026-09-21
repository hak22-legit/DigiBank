package com.bank.service;

import com.bank.model.entity.Budget;
import com.bank.model.entity.Category;
import com.bank.model.entity.User;
import com.bank.model.enums.BudgetPeriod;
import com.bank.model.enums.BudgetStatus;
import com.bank.model.repository.AccountRepository;
import com.bank.model.repository.BudgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private CategoryService categoryService;

    private BudgetService budgetService;
    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgetRepository, accountRepository, transactionService, categoryService);
        testUser = User.builder().userId(101L).username("testuser").build();
        testCategory = Category.builder().categoryId(5L).name("Dining Out").build();

        lenient().when(categoryService.getCategoryById(eq(5L), any(User.class))).thenReturn(testCategory);
    }

    @Test
    @DisplayName("createBudget creates and inserts new budget when no prior record exists for period")
    void testCreateBudgetWhenNotExists() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        BigDecimal limit = new BigDecimal("450.00");

        when(budgetRepository.findByUserAndCategoryAndPeriodAndStartDate(101L, 5L, BudgetPeriod.MONTHLY, start))
                .thenReturn(Optional.empty());

        when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> {
            Budget b = invocation.getArgument(0);
            b.setBudgetId(999L);
            return b;
        });

        Budget result = budgetService.createBudget(testUser, 5L, limit, BudgetPeriod.MONTHLY, start, end);

        assertNotNull(result);
        assertEquals(999L, result.getBudgetId());
        assertEquals(limit, result.getAmountLimit());
        assertEquals(BudgetStatus.ACTIVE, result.getStatus());

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).save(captor.capture());
        Budget saved = captor.getValue();
        assertEquals(101L, saved.getUserId());
        assertEquals(5L, saved.getCategoryId());
        assertEquals(BudgetPeriod.MONTHLY, saved.getPeriod());
    }

    @Test
    @DisplayName("createBudget updates existing budget when duplicate key tuple already exists (Upsert)")
    void testCreateBudgetWhenAlreadyExistsUpsert() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        BigDecimal oldLimit = new BigDecimal("200.00");
        BigDecimal newLimit = new BigDecimal("550.00");

        Budget existingBudget = Budget.builder()
                .budgetId(42L)
                .userId(101L)
                .categoryId(5L)
                .amountLimit(oldLimit)
                .period(BudgetPeriod.MONTHLY)
                .startDate(start)
                .endDate(end)
                .status(BudgetStatus.ACTIVE)
                .build();

        when(budgetRepository.findByUserAndCategoryAndPeriodAndStartDate(101L, 5L, BudgetPeriod.MONTHLY, start))
                .thenReturn(Optional.of(existingBudget));

        when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Budget result = budgetService.createBudget(testUser, 5L, newLimit, BudgetPeriod.MONTHLY, start, end);

        assertNotNull(result);
        assertEquals(42L, result.getBudgetId(), "Must preserve existing budget_id on upsert");
        assertEquals(newLimit, result.getAmountLimit(), "Amount limit must be updated to new value");

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).save(captor.capture());
        Budget saved = captor.getValue();
        assertEquals(42L, saved.getBudgetId());
        assertEquals(newLimit, saved.getAmountLimit());
    }

    @Test
    @DisplayName("createBudget rejects null or non-positive amount limit")
    void testCreateBudgetInvalidAmount() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        assertThrows(IllegalArgumentException.class, () ->
                budgetService.createBudget(testUser, 5L, BigDecimal.ZERO, BudgetPeriod.MONTHLY, start, end));
        assertThrows(IllegalArgumentException.class, () ->
                budgetService.createBudget(testUser, 5L, new BigDecimal("-10.00"), BudgetPeriod.MONTHLY, start, end));
        assertThrows(IllegalArgumentException.class, () ->
                budgetService.createBudget(testUser, 5L, null, BudgetPeriod.MONTHLY, start, end));

        verify(budgetRepository, never()).save(any());
    }
}
