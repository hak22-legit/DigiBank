package com.bank.controller;

import com.bank.model.TransactionView;
import com.bank.model.entity.User;
import com.bank.model.enums.HistoryFilter;
import com.bank.service.TransactionService;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    public List<TransactionView> getTransactionHistory(Long accountId, HistoryFilter filter, User user) {
        return transactionService.getTransactionHistory(accountId, filter, user);
    }
}
