package com.bank.model.dto;

import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AccountDTO {
    private Long accountId;
    private String accountNumber;
    private AccountType accountType;
    private BigDecimal balance;
    private Currency currency;
    private AccountStatus status;
    // userId intentionally omitted - internal FK, not needed by the console
    // (ownership is already enforced server-side via SessionManager)
}