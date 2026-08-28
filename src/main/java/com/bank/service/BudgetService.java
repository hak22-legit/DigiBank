package com.bank.service;

import com.bank.enums.BudgetStatus;
import com.bank.enums.BudgetUsageStatus;
import com.bank.enums.HistoryFilter;
import com.bank.enums.TransactionDirection;
import com.bank.exception.BudgetNotFoundException;
import com.bank.exception.CategoryNotFoundException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.*;
import com.bank.repository.AccountRepository;
import com.bank.repository.BudgetRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class BudgetService {

    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("80");

    private final BudgetRepository budgetRepository;
    private final AccountRepository accountRepository;
    private final TransactionService transactionService;
    private final CategoryService categoryService;

    public BudgetService(BudgetRepository budgetRepository,
                         AccountRepository accountRepository,
                         TransactionService transactionService,
                         CategoryService categoryService) {
        this.budgetRepository = budgetRepository;
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
        this.categoryService = categoryService;
    }

    /**
     * Creates a new budget for a specific category. The category must be
     * visible to the user (system category, or their own custom category).
     */
    public Budget createBudget(User user, Long categoryId, BigDecimal amountLimit,
                               com.bank.enums.BudgetPeriod period,
                               LocalDate startDate, LocalDate endDate) {

        // Validates the category exists and is visible to this user
        categoryService.getCategoryById(categoryId, user);

        if (amountLimit == null || amountLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Budget amount must be greater than zero");
        }

        Budget budget = Budget.builder()
                .userId(user.getUserId())
                .categoryId(categoryId)
                .amountLimit(amountLimit)
                .period(period)
                .startDate(startDate)
                .endDate(endDate)
                .status(BudgetStatus.ACTIVE)
                .build();

        return budgetRepository.save(budget);
    }

    /**
     * Returns all of a user's budgets, each enriched with actual spending
     * for that category within the budget's date range, and a usage status.
     */
    public List<BudgetView> getBudgetsWithUsage(User user) {
        List<Budget> budgets = budgetRepository.findByUserId(user.getUserId());
        return budgets.stream()
                .map(budget -> buildBudgetView(budget, user))
                .collect(Collectors.toList());
    }

    public BudgetView getBudgetUsage(Long budgetId, User user) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found: " + budgetId));

        if (!budget.getUserId().equals(user.getUserId())) {
            throw new UnauthorizedException("You do not have access to this budget");
        }

        return buildBudgetView(budget, user);
    }

    private BudgetView buildBudgetView(Budget budget, User user) {
        LocalDateTime rangeStart = budget.getStartDate().atStartOfDay();
        LocalDateTime rangeEnd = budget.getEndDate() != null
                ? budget.getEndDate().atTime(23, 59, 59)
                : LocalDateTime.now();

        // Sum all OUTCOME transactions in this category, across all of the
        // user's accounts, within the budget's date range.
        List<Account> accounts = accountRepository.findByUserId(user.getUserId());

        BigDecimal actualSpending = accounts.stream()
                .flatMap(acc -> transactionService.getTransactionHistory(
                        acc.getAccountId(), HistoryFilter.OUTCOME, rangeStart, rangeEnd, user).stream())
                .filter(v -> budget.getCategoryId().equals(v.getTransaction().getCategoryId()))
                .map(v -> v.getTransaction().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = budget.getAmountLimit().subtract(actualSpending);

        BigDecimal usagePercentage = BigDecimal.ZERO;
        if (budget.getAmountLimit().compareTo(BigDecimal.ZERO) > 0) {
            usagePercentage = actualSpending
                    .divide(budget.getAmountLimit(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        BudgetUsageStatus status;
        if (usagePercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            status = BudgetUsageStatus.EXCEEDED;
        } else if (usagePercentage.compareTo(WARNING_THRESHOLD) >= 0) {
            status = BudgetUsageStatus.WARNING;
        } else {
            status = BudgetUsageStatus.OK;
        }

        return BudgetView.builder()
                .budget(budget)
                .actualSpending(actualSpending)
                .remainingAmount(remaining)
                .usagePercentage(usagePercentage)
                .status(status)
                .build();
    }
}