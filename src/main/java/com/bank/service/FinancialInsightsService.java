package com.bank.service;

import com.bank.model.FinancialInsights;
import com.bank.model.TransactionView;
import com.bank.model.entity.Account;
import com.bank.model.entity.User;
import com.bank.model.enums.Currency;
import com.bank.model.enums.HistoryFilter;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.repository.AccountRepository;
import com.bank.util.CurrencyConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Enterprise financial insights service with multi-currency normalization.
 * Strictly adheres to the rule: Never perform direct mathematical summation
 * across mixed currencies (USD + KHR). Maintains separate aggregates per currency
 * and normalizes via live exchange rates.
 */
public class FinancialInsightsService {

    public record MultiCurrencyTotal(BigDecimal totalUsd, BigDecimal totalKhr) {
        public static MultiCurrencyTotal of(BigDecimal usd, BigDecimal khr) {
            return new MultiCurrencyTotal(
                    usd != null ? usd : BigDecimal.ZERO,
                    khr != null ? khr : BigDecimal.ZERO
            );
        }

        public BigDecimal toNormalizedUsd(Map<String, BigDecimal> rates) {
            BigDecimal usd = totalUsd != null ? totalUsd : BigDecimal.ZERO;
            BigDecimal khr = totalKhr != null ? totalKhr : BigDecimal.ZERO;
            if (khr.compareTo(BigDecimal.ZERO) == 0) return usd;
            BigDecimal khrInUsd = CurrencyConverter.convert(khr, "KHR", "USD", rates);
            return usd.add(khrInUsd);
        }
    }

    private final AccountRepository accountRepository;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final LiveCurrencyService liveCurrencyService;

    public FinancialInsightsService(AccountRepository accountRepository,
                                    TransactionService transactionService,
                                    CategoryService categoryService) {
        this(accountRepository, transactionService, categoryService, new LiveCurrencyService());
    }

    public FinancialInsightsService(AccountRepository accountRepository,
                                    TransactionService transactionService,
                                    CategoryService categoryService,
                                    LiveCurrencyService liveCurrencyService) {
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.liveCurrencyService = liveCurrencyService;
    }

    /**
     * Aggregates financial insights across ALL of a user's accounts,
     * scoped to the current calendar month with multi-currency normalization.
     */
    public FinancialInsights getCurrentMonthInsights(User user) {
        List<Account> accounts = accountRepository.findByUserId(user.getUserId());

        // 1. Maintain separate aggregates per currency for total balance
        BigDecimal balanceUsd = accounts.stream()
                .filter(a -> a.getCurrency() == Currency.USD)
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balanceKhr = accounts.stream()
                .filter(a -> a.getCurrency() == Currency.KHR)
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        MultiCurrencyTotal balanceBreakdown = MultiCurrencyTotal.of(balanceUsd, balanceKhr);

        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59);

        List<TransactionView> allViews = accounts.stream()
                .flatMap(acc -> transactionService.getTransactionHistory(
                        acc.getAccountId(), HistoryFilter.ALL, monthStart, monthEnd, user).stream())
                .collect(Collectors.toList());

        // 2. Maintain separate aggregates per currency for cash flow
        BigDecimal incomeUsd = sumByDirectionAndCurrency(allViews, TransactionDirection.INCOME, Currency.USD);
        BigDecimal incomeKhr = sumByDirectionAndCurrency(allViews, TransactionDirection.INCOME, Currency.KHR);
        MultiCurrencyTotal incomeBreakdown = MultiCurrencyTotal.of(incomeUsd, incomeKhr);

        BigDecimal expensesUsd = sumByDirectionAndCurrency(allViews, TransactionDirection.OUTCOME, Currency.USD);
        BigDecimal expensesKhr = sumByDirectionAndCurrency(allViews, TransactionDirection.OUTCOME, Currency.KHR);
        MultiCurrencyTotal expenseBreakdown = MultiCurrencyTotal.of(expensesUsd, expensesKhr);

        // 3. Normalize to base USD via exchange rates
        Map<String, BigDecimal> rates = (liveCurrencyService != null)
                ? liveCurrencyService.getRates()
                : CurrencyConverter.getFallbackRates();

        BigDecimal totalBalance = balanceBreakdown.toNormalizedUsd(rates);
        BigDecimal totalIncome = incomeBreakdown.toNormalizedUsd(rates);
        BigDecimal totalExpenses = expenseBreakdown.toNormalizedUsd(rates);
        BigDecimal monthlySavings = totalIncome.subtract(totalExpenses);

        BigDecimal savingsRate = BigDecimal.ZERO;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = monthlySavings
                    .divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // 4. Normalized category spending aggregation
        Optional<Map.Entry<Long, BigDecimal>> topCategory = allViews.stream()
                .filter(v -> v.getDirection() == TransactionDirection.OUTCOME)
                .filter(v -> v.getTransaction().getCategoryId() != null)
                .collect(Collectors.groupingBy(
                        v -> v.getTransaction().getCategoryId(),
                        Collectors.reducing(BigDecimal.ZERO, v -> {
                            BigDecimal amt = v.getTransaction().getAmount();
                            Currency cur = v.getTransaction().getCurrency();
                            if (cur == Currency.KHR) {
                                return CurrencyConverter.convert(amt, "KHR", "USD", rates);
                            }
                            return amt != null ? amt : BigDecimal.ZERO;
                        }, BigDecimal::add)))
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
                .balanceBreakdown(balanceBreakdown)
                .incomeBreakdown(incomeBreakdown)
                .expenseBreakdown(expenseBreakdown)
                .build();
    }

    private BigDecimal sumByDirectionAndCurrency(List<TransactionView> views, TransactionDirection direction, Currency currency) {
        return views.stream()
                .filter(v -> v.getDirection() == direction)
                .filter(v -> v.getTransaction().getCurrency() == currency)
                .map(v -> v.getTransaction().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}