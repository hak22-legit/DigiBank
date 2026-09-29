package com.bank.model.entity;

import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionStatus;
import com.bank.model.enums.TransactionType;
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

    public String getReferenceId() {
        String dateStr = (transactionDate != null)
                ? transactionDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                : (createdAt != null ? createdAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) : "20260928");
        long id = transactionId != null ? transactionId : 0L;
        return String.format("TXN-%s-%05d", dateStr, id);
    }
}