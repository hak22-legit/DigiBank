package com.bank.service;

import com.bank.database.DatabaseConnection;
import com.bank.enums.AccountStatus;
import com.bank.enums.AccountType;
import com.bank.enums.Currency;
import com.bank.enums.TransactionStatus;
import com.bank.enums.TransactionType;
import com.bank.exception.*;
import com.bank.model.Account;
import com.bank.model.Transaction;
import com.bank.model.User;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import com.bank.util.AccountNumberGenerator;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AccountService {

    private static final BigDecimal MAX_DEPOSIT_AMOUNT = new BigDecimal("1000000");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public Account createAccount(User owner, AccountType accountType, Currency currency) {
        String accountNumber = AccountNumberGenerator.generate();

        while (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            accountNumber = AccountNumberGenerator.generate();
        }

        Account account = Account.builder()
                .userId(owner.getUserId())
                .accountNumber(accountNumber)
                .accountType(accountType)
                .balance(BigDecimal.ZERO)
                .currency(currency)
                .status(AccountStatus.ACTIVE)
                .build();

        return accountRepository.save(account);
    }

    public Account getAccountById(Long accountId, User requestingUser) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        assertOwnership(account, requestingUser);
        return account;
    }

    public List<Account> getAccountsForUser(User user) {
        return accountRepository.findByUserId(user.getUserId());
    }

    public BigDecimal getBalance(Long accountId, User requestingUser) {
        Account account = getAccountById(accountId, requestingUser);
        return account.getBalance();
    }

    /**
     * Deposit money into an account.
     * Business rules:
     *  - amount > 0 and <= MAX_DEPOSIT_AMOUNT
     *  - account must exist and be ACTIVE
     *  - requesting user must own the account
     *  - if requestCurrency is null, defaults to the account's own currency
     *  - if requestCurrency is provided and doesn't match the account's currency -> reject
     * ACID: SELECT FOR UPDATE lock -> update balance -> insert transaction -> commit/rollback
     */
    public Transaction deposit(Long accountId, BigDecimal amount, Currency requestCurrency,
                               String description, User requestingUser) {

        validateAmount(amount);

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Lock the account row for the duration of this transaction
            Account account = accountRepository.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

            assertOwnership(account, requestingUser);

            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new AccountNotActiveException(
                        "Cannot deposit: account status is " + account.getStatus());
            }

            Currency effectiveCurrency = resolveCurrency(requestCurrency, account.getCurrency());

            // Update balance
            account.setBalance(account.getBalance().add(amount));
            accountRepository.updateWithConnection(conn, account);

            // Insert transaction record
            Transaction transaction = Transaction.builder()
                    .accountId(account.getAccountId())
                    .transactionType(TransactionType.DEPOSIT)
                    .amount(amount)
                    .currency(effectiveCurrency)
                    .description(description)
                    .status(TransactionStatus.COMPLETED)
                    .build();

            transaction = transactionRepository.saveWithConnection(conn, transaction);

            conn.commit();
            return transaction;

        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(conn);
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Deposit failed", e);
        } finally {
            closeQuietly(conn);
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Deposit amount must be greater than zero");
        }
        if (amount.compareTo(MAX_DEPOSIT_AMOUNT) > 0) {
            throw new InvalidAmountException(
                    "Deposit amount exceeds maximum allowed (" + MAX_DEPOSIT_AMOUNT + ")");
        }
    }

    private Currency resolveCurrency(Currency requestCurrency, Currency accountCurrency) {
        if (requestCurrency == null) {
            return accountCurrency;
        }
        if (requestCurrency != accountCurrency) {
            throw new CurrencyMismatchException(
                    "Currency mismatch: account is " + accountCurrency + ", request was " + requestCurrency);
        }
        return requestCurrency;
    }

    private void assertOwnership(Account account, User requestingUser) {
        if (!account.getUserId().equals(requestingUser.getUserId())) {
            throw new UnauthorizedException("You do not have access to this account");
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // logging will be added properly in Phase 25
            }
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException ignored) {
                // logging will be added properly in Phase 25
            }
        }
    }
}