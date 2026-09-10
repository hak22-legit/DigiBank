package com.bank.controller;

import com.bank.model.entity.SavingGoal;
import com.bank.model.entity.User;
import com.bank.service.SavingGoalService;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
public class SavingGoalController {
    private final SavingGoalService savingGoalService;

    public List<SavingGoal> getGoalsForUser(User user) {
        return savingGoalService.getGoalsForUser(user);
    }

    public SavingGoal createGoal(User user, String name, BigDecimal target, LocalDate deadline) {
        return savingGoalService.createGoal(user, name, target, deadline);
    }

    public SavingGoal contribute(Long goalId, BigDecimal amount, User user) {
        return savingGoalService.contribute(goalId, amount, user);
    }

    public BigDecimal getProgressPercentage(SavingGoal goal) {
        return savingGoalService.getProgressPercentage(goal);
    }

    public void cancelGoal(Long goalId, User user) {
        savingGoalService.cancelGoal(goalId, user);
    }
}
