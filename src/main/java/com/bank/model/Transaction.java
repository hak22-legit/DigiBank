package com.bank.model;

import com.bank.enums.Currency;
import com.bank.enums.TransactionStatus;
import com.bank.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    private Long transactionId;
    private Long accountId;
    private Long relatedAccountId;
    private Long categoryId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private Currency currency;
    private String description;
    private TransactionStatus status;
    private UUID idempotencyKey;
    private LocalDateTime transactionDate;
    private LocalDateTime createdAt;
}