package com.bank.model.entity;

import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
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
public class Account {
    private Long accountId;
    private Long userId;
    private String accountNumber;
    private AccountType accountType;
    private BigDecimal balance;
    private Currency currency;
    private AccountStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}