package com.bank.model.dto;

import com.bank.model.entity.Account;

import java.util.List;

public class AccountMapper {
    public static AccountDTO toDTO(Account account) {
        return AccountDTO.builder()
                .accountId(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .build();
    }

    public static List<AccountDTO> toDTOList(List<Account> accounts) {
        return accounts.stream().map(AccountMapper::toDTO).toList();
    }
}