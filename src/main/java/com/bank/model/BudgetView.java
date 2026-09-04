package com.bank.model;

import com.bank.model.entity.Budget;
import com.bank.model.enums.BudgetUsageStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BudgetView {
    private Budget budget;
    private BigDecimal actualSpending;
    private BigDecimal remainingAmount;
    private BigDecimal usagePercentage;
    private BudgetUsageStatus status;
}