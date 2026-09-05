package com.bank.model.dto;

import com.bank.model.enums.LoanStatus;
import com.bank.model.enums.RiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class LoanDTO {
    private Long loanId;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private RiskLevel riskLevel;
    // riskScore intentionally omitted - customers see the risk category
    // (LOW/MEDIUM/HIGH), not the granular internal score. LOAN_OFFICER
    // still sees the full Loan entity with riskScore via LoanApprovalService.
    private LoanStatus status;
    private BigDecimal outstandingBalance;
    private LocalDateTime createdAt;
}