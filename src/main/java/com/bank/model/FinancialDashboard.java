package com.bank.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class FinancialDashboard {
    private User user;
    private List<Account> accounts;
    private BigDecimal totalBalance;
    private FinancialInsights insights;
    private List<BudgetView> budgets;
    private List<SavingGoal> savingGoals;
}