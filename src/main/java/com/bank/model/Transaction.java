package com.bank.model;

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
    private Integer transactionId;
    private Integer accountId;
    private Integer relatedAccountId;
    private Integer categoryId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private String description;
    private UUID idempotencyKey;
    private TransactionStatus status;
    private LocalDateTime transactionDate;
}