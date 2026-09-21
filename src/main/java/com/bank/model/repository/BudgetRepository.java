package com.bank.model.repository;

import com.bank.model.entity.Budget;
import com.bank.model.enums.BudgetPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository {
    Optional<Budget> findById(Long budgetId);
    List<Budget> findByUserId(Long userId);
    List<Budget> findAll();
    Budget save(Budget budget);
    boolean deleteById(Long budgetId);
    Optional<Budget> findByUserAndCategoryAndPeriodAndStartDate(Long userId, Long categoryId, BudgetPeriod period, LocalDate startDate);
    Long upsertCategoryAndBudgetLimit(Long userId, Long categoryId, String categoryName, java.math.BigDecimal monthlyCap, int month, int year);
    Long upsertCategoryAndBudgetLimit(Long userId, String categoryName, java.math.BigDecimal monthlyCap, int month, int year);
    List<com.bank.model.dto.UnbudgetedCategory> getUnbudgetedCategories(Long userId, int month, int year);

    default Long saveBudget(Long userId, String categoryName, java.math.BigDecimal monthlyCap, int month, int year) {
        return upsertCategoryAndBudgetLimit(userId, null, categoryName, monthlyCap, month, year);
    }

    default Long saveBudget(Long userId, String categoryName, java.math.BigDecimal monthlyCap) {
        return upsertCategoryAndBudgetLimit(userId, null, categoryName, monthlyCap, 9, 2026);
    }
}