package com.bank.service;

import com.bank.enums.GoalStatus;
import com.bank.exception.InvalidAmountException;
import com.bank.exception.SavingGoalNotFoundException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.SavingGoal;
import com.bank.model.User;
import com.bank.repository.SavingGoalRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class SavingGoalService {

    private final SavingGoalRepository savingGoalRepository;

    public SavingGoalService(SavingGoalRepository savingGoalRepository) {
        this.savingGoalRepository = savingGoalRepository;
    }

    public SavingGoal createGoal(User user, String name, BigDecimal targetAmount, LocalDate deadline) {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Target amount must be greater than zero");
        }

        SavingGoal goal = SavingGoal.builder()
                .userId(user.getUserId())
                .name(name)
                .targetAmount(targetAmount)
                .currentAmount(BigDecimal.ZERO)
                .deadline(deadline)
                .status(GoalStatus.ACTIVE)
                .build();

        return savingGoalRepository.save(goal);
    }

    public List<SavingGoal> getGoalsForUser(User user) {
        return savingGoalRepository.findByUserId(user.getUserId());
    }

    public SavingGoal getGoalById(Long goalId, User requestingUser) {
        SavingGoal goal = savingGoalRepository.findById(goalId)
                .orElseThrow(() -> new SavingGoalNotFoundException("Saving goal not found: " + goalId));

        assertOwnership(goal, requestingUser);
        return goal;
    }

    /**
     * Manually adds an amount toward a goal's progress. This does NOT move
     * real money out of any account - it's a simple progress tracker.
     * If the goal reaches or exceeds its target, it's automatically marked COMPLETED.
     */
    public SavingGoal contribute(Long goalId, BigDecimal amount, User requestingUser) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Contribution amount must be greater than zero");
        }

        SavingGoal goal = getGoalById(goalId, requestingUser);

        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new IllegalStateException("Cannot contribute to a goal with status: " + goal.getStatus());
        }

        BigDecimal newAmount = goal.getCurrentAmount().add(amount);
        goal.setCurrentAmount(newAmount);

        if (newAmount.compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(GoalStatus.COMPLETED);
        }

        goal.setUpdatedAt(LocalDateTime.now());
        return savingGoalRepository.save(goal);
    }

    public BigDecimal getProgressPercentage(SavingGoal goal) {
        if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return goal.getCurrentAmount()
                .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public void cancelGoal(Long goalId, User requestingUser) {
        SavingGoal goal = getGoalById(goalId, requestingUser);
        goal.setStatus(GoalStatus.CANCELLED);
        goal.setUpdatedAt(LocalDateTime.now());
        savingGoalRepository.save(goal);
    }

    private void assertOwnership(SavingGoal goal, User requestingUser) {
        if (!goal.getUserId().equals(requestingUser.getUserId())) {
            throw new UnauthorizedException("You do not have access to this saving goal");
        }
    }
}