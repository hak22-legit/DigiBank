package com.bank.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Optional;

@Getter
@Builder
public class FinancialInsights {
    private BigDecimal totalBalance;
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal monthlySavings;
    private BigDecimal savingsRate;      // % = (income - expenses) / income * 100
    private Optional<String> highestSpendingCategory;
    private Optional<BigDecimal> highestSpendingAmount;
}