package com.bank.model;

import com.bank.enums.BudgetPeriod;
import com.bank.enums.BudgetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget {
    private Long budgetId;
    private Long userId;
    private Long categoryId;
    private BigDecimal amountLimit;
    private BudgetPeriod period;
    private LocalDate startDate;
    private LocalDate endDate;
    private BudgetStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}