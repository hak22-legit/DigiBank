package com.bank.repository;

import com.bank.model.SavingGoal;

import java.util.List;
import java.util.Optional;

public interface SavingGoalRepository {
    Optional<SavingGoal> findById(Long goalId);
    List<SavingGoal> findByUserId(Long userId);
    List<SavingGoal> findAll();
    SavingGoal save(SavingGoal goal);
    boolean deleteById(Long goalId);
}