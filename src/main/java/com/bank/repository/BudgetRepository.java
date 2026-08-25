package com.bank.repository;

import com.bank.model.Budget;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository {
    Optional<Budget> findById(Long budgetId);
    List<Budget> findByUserId(Long userId);
    List<Budget> findAll();
    Budget save(Budget budget);
    boolean deleteById(Long budgetId);
}