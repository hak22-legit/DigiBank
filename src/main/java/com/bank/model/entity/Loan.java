package com.bank.model.entity;

import com.bank.model.enums.LoanStatus;
import com.bank.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Loan {
    private Long loanId;
    private Long userId;
    private Long accountId;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private BigDecimal monthlyIncome;
    private BigDecimal monthlyExpense;
    private BigDecimal existingDebt;
    private Integer creditScore;
    private BigDecimal riskScore;
    private RiskLevel riskLevel;
    private LoanStatus status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String rejectionReason;
    private BigDecimal outstandingBalance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}