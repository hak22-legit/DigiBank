package com.bank.model;

import com.bank.enums.LoanPaymentStatus;
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
public class LoanPayment {
    private Long paymentId;
    private Long loanId;
    private Long accountId;
    private Long transactionId;
    private BigDecimal amount;
    private BigDecimal principalAmount;
    private BigDecimal interestAmount;
    private LocalDateTime paymentDate;
    private LocalDate dueDate;
    private LoanPaymentStatus status;
    private String paymentMethod;
    private LocalDateTime createdAt;
}