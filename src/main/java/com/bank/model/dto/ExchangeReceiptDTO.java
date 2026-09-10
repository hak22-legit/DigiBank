package com.bank.model.dto;

import com.bank.model.enums.Currency;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ExchangeReceiptDTO {
    private final BigDecimal sourceAmount;
    private final Currency sourceCurrency;
    private final BigDecimal convertedAmount;
    private final Currency targetCurrency;
    private final BigDecimal exchangeRate;
    private final BigDecimal fromAccountNewBalance;
    private final BigDecimal toAccountNewBalance;
}
