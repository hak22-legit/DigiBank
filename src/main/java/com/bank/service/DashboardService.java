package com.bank.service;

import com.bank.model.*;
import com.bank.model.entity.Account;
import com.bank.model.entity.SavingGoal;
import com.bank.model.entity.User;
import com.bank.model.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.List;

public class DashboardService {

    private final AccountRepository accountRepository;
    private final FinancialInsightsService insightsService;
    private final BudgetService budgetService;
    private final SavingGoalService savingGoalService;

    public DashboardService(AccountRepository accountRepository,
                            FinancialInsightsService insightsService,
                            BudgetService budgetService,
                            SavingGoalService savingGoalService) {
        this.accountRepository = accountRepository;
        this.insightsService = insightsService;
        this.budgetService = budgetService;
        this.savingGoalService = savingGoalService;
    }

    /**
     * Builds a full financial snapshot for a user by composing results
     * from every service built in Phases 7-15. No new business logic here -
     * this is pure aggregation.
     */
    public FinancialDashboard buildDashboard(User user) {
        List<Account> accounts = accountRepository.findByUserId(user.getUserId());

        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FinancialInsights insights = insightsService.getCurrentMonthInsights(user);
        List<BudgetView> budgets = budgetService.getBudgetsWithUsage(user);
        List<SavingGoal> goals = savingGoalService.getGoalsForUser(user);

        return FinancialDashboard.builder()
                .user(user)
                .accounts(accounts)
                .totalBalance(totalBalance)
                .insights(insights)
                .budgets(budgets)
                .savingGoals(goals)
                .build();
    }
}