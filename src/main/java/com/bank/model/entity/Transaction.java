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
    private BigDecimal destinationAmount;
    private Currency destinationCurrency;
    private String description;
    private TransactionStatus status;
    private UUID idempotencyKey;
    private LocalDateTime transactionDate;
    private LocalDateTime createdAt;

    private static final java.util.regex.Pattern EXCHANGE_PATTERN = java.util.regex.Pattern.compile(
            "Exchanged\\s+([0-9.,]+)\\s+([A-Za-z]{3})\\s*->\\s*([0-9.,]+)\\s+([A-Za-z]{3})",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private transient boolean exchangeInfoParsed;
    private transient boolean isCrossCurrencyDetected;

    private synchronized void parseExchangeInfo() {
        if (exchangeInfoParsed) return;
        exchangeInfoParsed = true;

        if (description != null && !description.isBlank()) {
            java.util.regex.Matcher m = EXCHANGE_PATTERN.matcher(description);
            if (m.find()) {
                isCrossCurrencyDetected = true;
                try {
                    String dstAmtStr = m.group(3).replace(",", "");
                    String dstCcyStr = m.group(4).toUpperCase();
                    if (this.destinationAmount == null) {
                        this.destinationAmount = new BigDecimal(dstAmtStr);
                    }
                    if (this.destinationCurrency == null) {
                        this.destinationCurrency = Currency.valueOf(dstCcyStr);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public BigDecimal getDestinationAmount() {
        if (destinationAmount != null) {
            return destinationAmount;
        }
        parseExchangeInfo();
        if (destinationAmount != null) {
            return destinationAmount;
        }
        return amount != null ? amount : BigDecimal.ZERO;
    }

    public Currency getDestinationCurrency() {
        if (destinationCurrency != null) {
            return destinationCurrency;
        }
        parseExchangeInfo();
        if (destinationCurrency != null) {
            return destinationCurrency;
        }
        return currency != null ? currency : Currency.USD;
    }

    public boolean isCrossCurrency() {
        if (destinationCurrency != null && currency != null && destinationCurrency != currency) {
            return true;
        }
        parseExchangeInfo();
        return isCrossCurrencyDetected;
    }

    public boolean isDestinationAccount(Long targetAccountId) {
        return targetAccountId != null && targetAccountId.equals(relatedAccountId);
    }

    public boolean isSourceAccount(Long targetAccountId) {
        return targetAccountId != null && targetAccountId.equals(accountId);
    }

    public BigDecimal getAmountForAccount(Long targetAccountId) {
        if (isDestinationAccount(targetAccountId)) {
            return getDestinationAmount();
        }
        return amount != null ? amount : BigDecimal.ZERO;
    }

    public Currency getCurrencyForAccount(Long targetAccountId) {
        if (isDestinationAccount(targetAccountId)) {
            return getDestinationCurrency();
        }
        return currency != null ? currency : Currency.USD;
    }

    public String getReferenceId() {
        String dateStr = (transactionDate != null)
                ? transactionDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                : (createdAt != null ? createdAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) : "20260928");
        long id = transactionId != null ? transactionId : 0L;
        return String.format("TXN-%s-%05d", dateStr, id);
    }
}