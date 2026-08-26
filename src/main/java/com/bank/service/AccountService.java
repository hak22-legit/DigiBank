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

public class AccountService {

    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("1000000");

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
     * Deposit money into an account. See Phase 8 for full rule explanation.
     */
    public Transaction deposit(Long accountId, BigDecimal amount, Currency requestCurrency,
                               String description, User requestingUser) {

        validateAmount(amount);

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account account = accountRepository.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

            assertOwnership(account, requestingUser);
            assertActive(account);

            Currency effectiveCurrency = resolveCurrency(requestCurrency, account.getCurrency());

            account.setBalance(account.getBalance().add(amount));
            accountRepository.updateWithConnection(conn, account);

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

    /**
     * Withdraw money from an account.
     * ច្បាប់ធនាគារ៖
     *  - amount > 0 និង <= MAX_TRANSACTION_AMOUNT
     *  - account ត្រូវតែមាន, ជា ACTIVE, ជាកម្មសិទ្ធិរបស់ user ដែលស្នើសុំ
     *  - ត្រូវការ balance គ្រប់គ្រាន់ (គ្មាន overdraft)
     *  - currency resolution ដូច deposit (default ទៅ account currency, បដិសេធបើមិនត្រូវគ្នា)
     * ACID: SELECT FOR UPDATE lock -> ត្រួតពិនិត្យ balance -> update balance -> insert transaction -> commit/rollback
     */
    public Transaction withdraw(Long accountId, BigDecimal amount, Currency requestCurrency,
                                String description, User requestingUser) {

        validateAmount(amount);

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account account = accountRepository.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

            assertOwnership(account, requestingUser);
            assertActive(account);

            Currency effectiveCurrency = resolveCurrency(requestCurrency, account.getCurrency());

            if (account.getBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient balance: available " + account.getBalance() + ", requested " + amount);
            }

            account.setBalance(account.getBalance().subtract(amount));
            accountRepository.updateWithConnection(conn, account);

            Transaction transaction = Transaction.builder()
                    .accountId(account.getAccountId())
                    .transactionType(TransactionType.WITHDRAWAL)
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
            throw new RuntimeException("Withdrawal failed", e);
        } finally {
            closeQuietly(conn);
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (amount.compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            throw new InvalidAmountException(
                    "Amount exceeds maximum allowed (" + MAX_TRANSACTION_AMOUNT + ")");
        }
    }

    private void assertActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Operation not allowed: account status is " + account.getStatus());
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
                // logging ត្រឹមត្រូវនឹងបន្ថែមនៅ Phase 25
            }
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException ignored) {
                // logging ត្រឹមត្រូវនឹងបន្ថែមនៅ Phase 25
            }
        }
    }
}