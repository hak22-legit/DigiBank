package com.bank.model;

import com.bank.enums.LoanStatus;
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
    private Integer loanId;
    private Integer userId;
    private Integer approvedBy;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private BigDecimal monthlyIncome;
    private BigDecimal monthlyExpense;
    private BigDecimal existingDebt;
    private Integer creditScore;
    private BigDecimal riskScore;
    private String currency;
    private LoanStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}