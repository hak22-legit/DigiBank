package com.bank.controller;

import com.bank.model.entity.Budget;
import com.bank.model.BudgetView;
import com.bank.model.enums.Currency;
import com.bank.model.enums.BudgetPeriod;
import com.bank.model.entity.User;
import com.bank.service.BudgetService;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
public class BudgetController {
    private final BudgetService budgetService;

    public List<BudgetView> getBudgetsWithUsage(User user) {
        return budgetService.getBudgetsWithUsage(user);
    }

    public Budget createBudget(User user, Long categoryId, BigDecimal limit, BudgetPeriod period, LocalDate start, LocalDate end) {
        return budgetService.createBudget(user, categoryId, limit, period, start, end);
    }

    public boolean deleteBudget(User user, Long budgetId) {
        return budgetService.deleteBudget(budgetId, user);
    }
}
