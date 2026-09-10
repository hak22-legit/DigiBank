package com.bank.service;

import com.bank.database.DatabaseConnection;
import com.bank.exception.*;
import com.bank.model.dto.ExchangeReceiptDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionStatus;
import com.bank.model.enums.TransactionType;
import com.bank.model.repository.AccountRepository;
import com.bank.model.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class CurrencyExchangeService {

    private static final BigDecimal USD_KHR_RATE = new BigDecimal("4000");
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public CurrencyExchangeService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public ExchangeReceiptDTO exchange(User user, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Exchange amount must be greater than zero");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        Long firstLockId = Math.min(fromAccountId, toAccountId);
        Long secondLockId = Math.max(fromAccountId, toAccountId);

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account firstLocked = accountRepository.findByIdForUpdate(conn, firstLockId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstLockId));
            Account secondLocked = accountRepository.findByIdForUpdate(conn, secondLockId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondLockId));

            Account fromAccount = fromAccountId.equals(firstLockId) ? firstLocked : secondLocked;
            Account toAccount = fromAccountId.equals(firstLockId) ? secondLocked : firstLocked;

            // Validations
            if (!fromAccount.getUserId().equals(user.getUserId())) {
                throw new UnauthorizedException("Source account does not belong to the requesting user");
            }
            if (!toAccount.getUserId().equals(user.getUserId())) {
                throw new UnauthorizedException("Destination account does not belong to the requesting user");
            }
            if (fromAccount.getStatus() != com.bank.model.enums.AccountStatus.ACTIVE) {
                throw new AccountNotActiveException("Source account is not active");
            }
            if (toAccount.getStatus() != com.bank.model.enums.AccountStatus.ACTIVE) {
                throw new AccountNotActiveException("Destination account is not active");
            }
            if (fromAccount.getCurrency() == toAccount.getCurrency()) {
                throw new SameCurrencyException("Both accounts have the same currency (" + fromAccount.getCurrency() + "). Please use a regular transfer.");
            }
            if (fromAccount.getBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException("Insufficient balance for exchange. Available: " + fromAccount.getBalance());
            }

            // Calculate conversion
            BigDecimal convertedAmount;
            BigDecimal rateUsed;
            if (fromAccount.getCurrency() == Currency.USD && toAccount.getCurrency() == Currency.KHR) {
                convertedAmount = amount.multiply(USD_KHR_RATE).setScale(4, RoundingMode.HALF_UP);
                rateUsed = USD_KHR_RATE;
            } else if (fromAccount.getCurrency() == Currency.KHR && toAccount.getCurrency() == Currency.USD) {
                convertedAmount = amount.divide(USD_KHR_RATE, 4, RoundingMode.HALF_UP);
                rateUsed = BigDecimal.ONE.divide(USD_KHR_RATE, 4, RoundingMode.HALF_UP);
            } else {
                throw new CurrencyMismatchException("Unsupported currency pair for exchange");
            }

            // Update balances
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            toAccount.setBalance(toAccount.getBalance().add(convertedAmount));

            accountRepository.updateWithConnection(conn, fromAccount);
            accountRepository.updateWithConnection(conn, toAccount);

            // Create transaction record (using TRANSFER type as per requirements)
            String description = String.format("Currency exchange: %s %s -> %s %s (rate: %s)",
                    amount.setScale(4, RoundingMode.HALF_UP), fromAccount.getCurrency(),
                    convertedAmount.setScale(4, RoundingMode.HALF_UP), toAccount.getCurrency(),
                    rateUsed.setScale(4, RoundingMode.HALF_UP));

            Transaction transaction = Transaction.builder()
                    .accountId(fromAccount.getAccountId())
                    .relatedAccountId(toAccount.getAccountId())
                    .transactionType(TransactionType.TRANSFER)
                    .amount(amount)
                    .currency(fromAccount.getCurrency())
                    .description(description)
                    .status(TransactionStatus.COMPLETED)
                    .build();

            transactionRepository.saveWithConnection(conn, transaction);

            conn.commit();

            return ExchangeReceiptDTO.builder()
                    .sourceAmount(amount)
                    .sourceCurrency(fromAccount.getCurrency())
                    .convertedAmount(convertedAmount)
                    .targetCurrency(toAccount.getCurrency())
                    .exchangeRate(rateUsed)
                    .fromAccountNewBalance(fromAccount.getBalance())
                    .toAccountNewBalance(toAccount.getBalance())
                    .build();

        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(conn);
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Currency exchange failed", e);
        } finally {
            closeQuietly(conn);
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException ignored) {}
        }
    }
}
