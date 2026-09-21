package com.bank.service;

import com.bank.model.entity.Account;
import com.bank.model.entity.Budget;
import com.bank.model.entity.User;
import com.bank.model.enums.*;
import com.bank.exception.BudgetNotFoundException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.*;
import com.bank.model.repository.AccountRepository;
import com.bank.model.repository.BudgetRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
                               BudgetPeriod period,
                               LocalDate startDate, LocalDate endDate) {

        // Validates the category exists and is visible to this user
        categoryService.getCategoryById(categoryId, user);

        if (amountLimit == null || amountLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Budget amount must be greater than zero");
        }

        BudgetPeriod effectivePeriod = (period != null) ? period : BudgetPeriod.MONTHLY;

        // Check if a budget record already exists for (user_id, category_id, period, start_date)
        Optional<Budget> existingOpt = budgetRepository.findByUserAndCategoryAndPeriodAndStartDate(
                user.getUserId(), categoryId, effectivePeriod, startDate);

        if (existingOpt.isPresent()) {
            Budget existing = existingOpt.get();
            existing.setAmountLimit(amountLimit);
            existing.setEndDate(endDate);
            existing.setStatus(BudgetStatus.ACTIVE);
            return budgetRepository.save(existing);
        }

        Budget budget = Budget.builder()
                .userId(user.getUserId())
                .categoryId(categoryId)
                .amountLimit(amountLimit)
                .period(effectivePeriod)
                .startDate(startDate)
                .endDate(endDate)
                .status(BudgetStatus.ACTIVE)
                .build();

        return budgetRepository.save(budget);
    }

    /**
     * Returns all of a user's budgets, each enriched with actual spending
     * for that category within the budget's date range, and a usage status.
     * Batch-fetches transactions across all accounts once to eliminate N*M database roundtrips.
     */
    public List<BudgetView> getBudgetsWithUsage(User user) {
        List<Budget> budgets = budgetRepository.findByUserId(user.getUserId());
        if (budgets == null || budgets.isEmpty()) {
            return List.of();
        }

        List<Account> accounts = accountRepository.findByUserId(user.getUserId());
        if (accounts == null || accounts.isEmpty()) {
            return budgets.stream()
                    .map(b -> calculateBudgetView(b, BigDecimal.ZERO))
                    .collect(Collectors.toList());
        }

        LocalDateTime overallStart = budgets.stream()
                .map(b -> b.getStartDate().atStartOfDay())
                .min(LocalDateTime::compareTo)
                .orElse(LocalDate.now().withDayOfMonth(1).atStartOfDay());

        LocalDateTime overallEnd = budgets.stream()
                .map(b -> b.getEndDate() != null ? b.getEndDate().atTime(23, 59, 59) : LocalDateTime.now())
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        // Batch fetch all OUTCOME transactions across user's accounts within overall date window once
        List<TransactionView> allTransactions = accounts.stream()
                .flatMap(acc -> transactionService.getTransactionHistory(
                        acc.getAccountId(), HistoryFilter.OUTCOME, overallStart, overallEnd, user).stream())
                .collect(Collectors.toList());

        return budgets.stream()
                .map(budget -> buildBudgetViewWithTransactions(budget, allTransactions))
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

        List<Account> accounts = accountRepository.findByUserId(user.getUserId());

        BigDecimal actualSpending = accounts.stream()
                .flatMap(acc -> transactionService.getTransactionHistory(
                        acc.getAccountId(), HistoryFilter.OUTCOME, rangeStart, rangeEnd, user).stream())
                .filter(v -> v.getTransaction() != null && budget.getCategoryId().equals(v.getTransaction().getCategoryId()))
                .map(v -> v.getTransaction().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return calculateBudgetView(budget, actualSpending);
    }

    private BudgetView buildBudgetViewWithTransactions(Budget budget, List<TransactionView> allTransactions) {
        LocalDateTime rangeStart = budget.getStartDate().atStartOfDay();
        LocalDateTime rangeEnd = budget.getEndDate() != null
                ? budget.getEndDate().atTime(23, 59, 59)
                : LocalDateTime.now();

        BigDecimal actualSpending = allTransactions.stream()
                .filter(v -> v.getTransaction() != null
                        && budget.getCategoryId().equals(v.getTransaction().getCategoryId())
                        && v.getTransaction().getCreatedAt() != null
                        && !v.getTransaction().getCreatedAt().isBefore(rangeStart)
                        && !v.getTransaction().getCreatedAt().isAfter(rangeEnd))
                .map(v -> v.getTransaction().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return calculateBudgetView(budget, actualSpending);
    }

    private BudgetView calculateBudgetView(Budget budget, BigDecimal actualSpending) {
        BigDecimal spending = actualSpending != null ? actualSpending : BigDecimal.ZERO;
        BigDecimal remaining = budget.getAmountLimit().subtract(spending);

        BigDecimal usagePercentage = BigDecimal.ZERO;
        if (budget.getAmountLimit().compareTo(BigDecimal.ZERO) > 0) {
            usagePercentage = spending
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
                .actualSpending(spending)
                .remainingAmount(remaining)
                .usagePercentage(usagePercentage)
                .status(status)
                .build();
    }

    public boolean deleteBudget(Long budgetId, User user) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found: " + budgetId));

        if (!budget.getUserId().equals(user.getUserId())) {
            throw new UnauthorizedException("You do not have access to this budget");
        }

        return budgetRepository.deleteById(budgetId);
    }

    public List<com.bank.model.dto.UnbudgetedCategory> getUnbudgetedCategories(User user, int month, int year) {
        return budgetRepository.getUnbudgetedCategories(user.getUserId(), month, year);
    }

    public Long configureBudget(User user, String categoryName, BigDecimal monthlyCap, int month, int year) {
        return configureBudget(user, null, categoryName, monthlyCap, month, year);
    }

    public Long configureBudget(User user, Long categoryId, String categoryName, BigDecimal monthlyCap, int month, int year) {
        String trimmed = (categoryName != null) ? categoryName.trim() : "";
        if (categoryId == null && (trimmed.length() < 3 || trimmed.length() > 20)) {
            throw new IllegalArgumentException("Category name must be 3-20 characters");
        }
        if (monthlyCap == null || monthlyCap.compareTo(new BigDecimal("1.00")) < 0) {
            throw new IllegalArgumentException("Monthly Cap must be at least $1.00");
        }
        return budgetRepository.upsertCategoryAndBudgetLimit(user.getUserId(), categoryId, trimmed, monthlyCap.setScale(2, RoundingMode.HALF_UP), month, year);
    }
}