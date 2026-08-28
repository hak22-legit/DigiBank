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
import java.util.UUID;

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
     * ផ្ទេរប្រាក់រវាង account ពីរ។
     * ច្បាប់ធនាគារ៖
     *  - amount > 0 និង <= MAX_TRANSACTION_AMOUNT
     *  - account អ្នកផ្ញើ និងអ្នកទទួល ត្រូវតែផ្សេងគ្នា
     *  - account អ្នកផ្ញើ ត្រូវជាកម្មសិទ្ធិរបស់ user ដែលស្នើសុំ (account អ្នកទទួល ជាកម្មសិទ្ធិអ្នកណាក៏បាន)
     *  - account ទាំងពីរត្រូវជា ACTIVE
     *  - account ទាំងពីរត្រូវប្រើ currency ដូចគ្នា (គ្មាន conversion)
     *  - balance គ្រប់គ្រាន់លើ sender (គ្មាន overdraft)
     *  - idempotency: caller ត្រូវតែផ្តល់ UUID; ការព្យាយាមម្តងទៀតជាមួយ key ដដែល
     *    នឹងត្រឡប់ transaction ដើមវិញ ជំនួសឱ្យផ្ទេរម្តងទៀត
     *  - deterministic lock ordering (lock account_id តូចជាងមុន) ដើម្បីជៀសវាង deadlock
     *    ពេលមានការផ្ទេររវាង account ពីរដូចគ្នា កើតឡើងក្នុងពេលដំណាលគ្នា
     * ACID: lock account ទាំងពីរ -> validate -> debit sender -> credit receiver ->
     *       insert transaction record តែមួយ -> commit/rollback
     */
    public Transaction transfer(Long senderAccountId, Long receiverAccountId, BigDecimal amount,
                                Currency requestCurrency, String description,
                                UUID idempotencyKey, User requestingUser) {

        if (idempotencyKey == null) {
            throw new InvalidTransferException("Idempotency key is required for transfers");
        }
        if (senderAccountId.equals(receiverAccountId)) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }
        validateAmount(amount);

        // ការត្រួតពិនិត្យ idempotency: បើ request ដូចនេះធ្លាប់ដំណើរការរួចហើយ ត្រឡប់លទ្ធផលចាស់វិញ
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Lock ត្រឹមត្រូវតាមលំដាប់៖ lock account_id តូចជាងជានិច្ចមុនគេ
        // នេះជាមូលហេតុជៀសវាង deadlock ពេល Transfer A (X->Y) និង Transfer B (Y->X)
        // ដំណើរការក្នុងពេលដំណាលគ្នា - ទាំងពីរនឹងព្យាយាម lock តាមលំដាប់ដូចគ្នា
        Long firstLockId = Math.min(senderAccountId, receiverAccountId);
        Long secondLockId = Math.max(senderAccountId, receiverAccountId);

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account firstLocked = accountRepository.findByIdForUpdate(conn, firstLockId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstLockId));
            Account secondLocked = accountRepository.findByIdForUpdate(conn, secondLockId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondLockId));

            // ត្រឡប់ទៅតួនាទី sender/receiver វិញ ទោះបីជា lock តាមលំដាប់ណាក៏ដោយ
            Account sender = senderAccountId.equals(firstLockId) ? firstLocked : secondLocked;
            Account receiver = senderAccountId.equals(firstLockId) ? secondLocked : firstLocked;

            assertOwnership(sender, requestingUser);
            assertActive(sender);
            assertActive(receiver);
            assertSameCurrency(sender, receiver);

            Currency effectiveCurrency = resolveCurrency(requestCurrency, sender.getCurrency());

            if (sender.getBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient balance: available " + sender.getBalance() + ", requested " + amount);
            }

            sender.setBalance(sender.getBalance().subtract(amount));
            receiver.setBalance(receiver.getBalance().add(amount));

            accountRepository.updateWithConnection(conn, sender);
            accountRepository.updateWithConnection(conn, receiver);

            Transaction transaction = Transaction.builder()
                    .accountId(sender.getAccountId())
                    .relatedAccountId(receiver.getAccountId())
                    .transactionType(TransactionType.TRANSFER)
                    .amount(amount)
                    .currency(effectiveCurrency)
                    .description(description)
                    .status(TransactionStatus.COMPLETED)
                    .idempotencyKey(idempotencyKey)
                    .build();

            transaction = transactionRepository.saveWithConnection(conn, transaction);

            conn.commit();
            return transaction;

        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(conn);

            // ការការពារ race condition៖ បើ request ដំណាលគ្នា ២ ប្រើ idempotency key ដូចគ្នា
            // មួយ commit មុន ម្យ៉ាងទៀតប៉ះ UNIQUE constraint នៅ DB level
            // ជំនួសឱ្យបោះ error ដ៏គួរឱ្យខ្លាច យើងចាប់វា ហើយត្រឡប់ transaction
            // ដែល request ដទៃបានបង្កើតរួចហើយ
            if (isUniqueViolation(e)) {
                return transactionRepository.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new RuntimeException("Transfer failed and could not recover", e));
            }

            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Transfer failed", e);
        } finally {
            closeQuietly(conn);
        }
    }

    private void assertSameCurrency(Account sender, Account receiver) {
        if (sender.getCurrency() != receiver.getCurrency()) {
            throw new CurrencyMismatchException(
                    "Cannot transfer between accounts with different currencies: "
                            + sender.getCurrency() + " -> " + receiver.getCurrency());
        }
    }

    private boolean isUniqueViolation(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Deposit money into an account. See Phase 8 for full rule explanation.
     */
    public Transaction deposit(Long accountId, BigDecimal amount, Currency requestCurrency,
                               String description, Long categoryId, User requestingUser) {

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
                    .categoryId(categoryId)   // បន្ថែមបន្ទាត់នេះ
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
                                String description, Long categoryId, User requestingUser) {

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
                    .categoryId(categoryId)   // បន្ថែមបន្ទាត់នេះ
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