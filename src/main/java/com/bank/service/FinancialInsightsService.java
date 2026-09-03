package com.bank.service;

import com.bank.enums.TransactionDirection;
import com.bank.enums.HistoryFilter;
import com.bank.model.*;
import com.bank.repository.AccountRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class FinancialInsightsService {

    private final AccountRepository accountRepository;
    private final TransactionService transactionService;
    private final CategoryService categoryService;

    public FinancialInsightsService(AccountRepository accountRepository,
                                    TransactionService transactionService,
                                    CategoryService categoryService) {
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
        this.categoryService = categoryService;
    }

    /**
     * Aggregates financial insights across ALL of a user's accounts,
     * scoped to the current calendar month.
     */
    public FinancialInsights getCurrentMonthInsights(User user) {
        List<Account> accounts = accountRepository.findByUserId(user.getUserId());

        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59);

        List<TransactionView> allViews = accounts.stream()
                .flatMap(acc -> transactionService.getTransactionHistory(
                        acc.getAccountId(), HistoryFilter.ALL, monthStart, monthEnd, user).stream())
                .collect(Collectors.toList());

        BigDecimal totalIncome = sumByDirection(allViews, TransactionDirection.INCOME);
        BigDecimal totalExpenses = sumByDirection(allViews, TransactionDirection.OUTCOME);
        BigDecimal monthlySavings = totalIncome.subtract(totalExpenses);

        BigDecimal savingsRate = BigDecimal.ZERO;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = monthlySavings
                    .divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        Optional<Map.Entry<Long, BigDecimal>> topCategory = allViews.stream()
                .filter(v -> v.getDirection() == TransactionDirection.OUTCOME)
                .filter(v -> v.getTransaction().getCategoryId() != null)
                .collect(Collectors.groupingBy(
                        v -> v.getTransaction().getCategoryId(),
                        Collectors.reducing(BigDecimal.ZERO, v -> v.getTransaction().getAmount(), BigDecimal::add)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue());

        Optional<String> topCategoryName = topCategory.map(entry ->
                categoryService.getCategoryById(entry.getKey(), user).getName());
        Optional<BigDecimal> topCategoryAmount = topCategory.map(Map.Entry::getValue);

        return FinancialInsights.builder()
                .totalBalance(totalBalance)
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .monthlySavings(monthlySavings)
                .savingsRate(savingsRate)
                .highestSpendingCategory(topCategoryName)
                .highestSpendingAmount(topCategoryAmount)
                .build();
    }

    private BigDecimal sumByDirection(List<TransactionView> views, TransactionDirection direction) {
        return views.stream()
                .filter(v -> v.getDirection() == direction)
                .map(v -> v.getTransaction().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}