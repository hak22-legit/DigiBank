package com.bank.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row in a bank statement PDF - field names must match the .jrxml
 * <field> declarations exactly (transactionDate, description, transactionType,
 * amount, runningBalance).
 */
public class TransactionLineDTO {
    private final LocalDateTime transactionDate;
    private final String description;
    private final String transactionType;
    private final BigDecimal amount;
    private final BigDecimal runningBalance;

    public TransactionLineDTO(LocalDateTime transactionDate, String description,
                              String transactionType, BigDecimal amount,
                              BigDecimal runningBalance) {
        this.transactionDate = transactionDate;
        this.description = description;
        this.transactionType = transactionType;
        this.amount = amount;
        this.runningBalance = runningBalance;
    }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public String getDescription() { return description; }
    public String getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getRunningBalance() { return runningBalance; }
}