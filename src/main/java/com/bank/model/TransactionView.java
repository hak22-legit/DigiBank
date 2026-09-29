package com.bank.model;

import com.bank.model.entity.Transaction;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionDirection;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class TransactionView {
    private final Transaction transaction;
    private final TransactionDirection direction;
    private final BigDecimal settledAmount;
    private final Currency settledCurrency;

    public TransactionView(Transaction transaction, TransactionDirection direction) {
        this(transaction, direction, null, null);
    }

    public TransactionView(Transaction transaction, TransactionDirection direction,
                           BigDecimal settledAmount, Currency settledCurrency) {
        this.transaction = transaction;
        this.direction = direction;

        if (settledAmount != null) {
            this.settledAmount = settledAmount;
        } else if (transaction != null) {
            if (direction == TransactionDirection.INCOME) {
                this.settledAmount = transaction.getDestinationAmount();
            } else {
                this.settledAmount = transaction.getAmount() != null ? transaction.getAmount() : BigDecimal.ZERO;
            }
        } else {
            this.settledAmount = BigDecimal.ZERO;
        }

        if (settledCurrency != null) {
            this.settledCurrency = settledCurrency;
        } else if (transaction != null) {
            if (direction == TransactionDirection.INCOME) {
                this.settledCurrency = transaction.getDestinationCurrency();
            } else {
                this.settledCurrency = transaction.getCurrency() != null ? transaction.getCurrency() : Currency.USD;
            }
        } else {
            this.settledCurrency = Currency.USD;
        }
    }

    public BigDecimal getDisplayAmount() {
        return settledAmount;
    }

    public Currency getDisplayCurrency() {
        return settledCurrency;
    }
}