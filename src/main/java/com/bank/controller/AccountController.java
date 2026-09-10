package com.bank.controller;

import com.bank.model.entity.Account;
import com.bank.model.enums.Currency;
import com.bank.model.dto.AccountDTO;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountType;
import com.bank.service.AccountService;
import com.bank.service.CurrencyExchangeService;
import com.bank.model.repository.AccountRepository;
import com.bank.model.dto.ExchangeReceiptDTO;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;
    private final CurrencyExchangeService exchangeService;
    private final AccountRepository accountRepo;

    public List<AccountDTO> getAccountsForUser(User user) {
        return accountService.getAccountsForUser(user);
    }

    public AccountDTO createAccount(User user, AccountType type, Currency currency) {
        return accountService.createAccount(user, type, currency);
    }

    public Transaction deposit(Long accountId, BigDecimal amount, Currency currency, String desc, Long categoryId, User user) {
        return accountService.deposit(accountId, amount, currency, desc, categoryId, user);
    }

    public Transaction withdraw(Long accountId, BigDecimal amount, Currency currency, String desc, Long categoryId, User user) {
        return accountService.withdraw(accountId, amount, currency, desc, categoryId, user);
    }

    public Transaction transfer(Long senderId, Long receiverId, BigDecimal amount, Currency currency, String desc, User user) {
        return accountService.transfer(senderId, receiverId, amount, currency, desc, UUID.randomUUID(), user);
    }

    public BigDecimal getBalance(Long accountId, User user) {
        return accountService.getBalance(accountId, user);
    }

    public Account findAccountByNumber(String accountNumber) {
        return accountRepo.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }

    public ExchangeReceiptDTO exchangeCurrency(User user, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        return exchangeService.exchange(user, fromAccountId, toAccountId, amount);
    }
}
