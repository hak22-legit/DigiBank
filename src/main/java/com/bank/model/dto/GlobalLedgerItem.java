package com.bank.model.dto;

import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalLedgerItem {
    private Long accountId;
    private String accountNumber;
    private Long userId;
    private String ownerName;
    private AccountType accountType;
    private Currency currency;
    private BigDecimal balance;
    private String riskLevel; // e.g., "LOW", "MED", "HIGH"
    private AccountStatus status;
}
