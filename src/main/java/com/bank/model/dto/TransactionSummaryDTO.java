package com.bank.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryDTO {
    private Long transactionId;
    private LocalDateTime createdAt;
    private String transactionType;
    private BigDecimal amount;
    private String destination;
    private String status;
    private String currency;
}
