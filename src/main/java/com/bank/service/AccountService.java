package com.bank.service;

import com.bank.database.DatabaseConnection;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.AccountMapper;
import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionStatus;
import com.bank.model.enums.TransactionType;
import com.bank.exception.*;
import com.bank.model.entity.Account;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.repository.AccountRepository;
import com.bank.model.repository.TransactionRepository;
import com.bank.util.AccountNumberGenerator;
import com.bank.util.CurrencyConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AccountService {

    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("1000000");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final FraudDetectionService fraudDetectionService;
    private final LiveCurrencyService liveCurrencyService;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          FraudDetectionService fraudDetectionService) {
        this(accountRepository, transactionRepository, fraudDetectionService, new LiveCurrencyService());
    }

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          FraudDetectionService fraudDetectionService,
                          LiveCurrencyService liveCurrencyService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.fraudDetectionService = fraudDetectionService;
        this.liveCurrencyService = liveCurrencyService;
    }

    public BigDecimal getBalance(Long accountId, User requestingUser) {
        Account account = getAccountById(accountId, requestingUser);
        return account.getBalance();
    }
    public AccountDTO createAccount(User owner, AccountType accountType, Currency currency) {
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

        Account saved = accountRepository.save(account);
        return AccountMapper.toDTO(saved);
    }

    /**
     * Creates a new bank account with optional atomic funding from an existing account.
     */
    public AccountDTO createAndFundAccount(User owner, AccountType accountType, Currency currency,
                                          BigDecimal initialDeposit, Long fundingAccountId) {
        if (owner == null) {
            throw new UnauthorizedException("User session is required to open an account");
        }
        if (accountType == null) {
            throw new IllegalArgumentException("Account type is required");
        }
        if (currency == null) {
            currency = Currency.USD;
        }

        BigDecimal deposit = (initialDeposit != null) ? initialDeposit : BigDecimal.ZERO;
        if (deposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException("Initial deposit cannot be negative");
        }

        String accountNumber = AccountNumberGenerator.generate();
        while (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            accountNumber = AccountNumberGenerator.generate();
        }

        if (deposit.compareTo(BigDecimal.ZERO) == 0 || fundingAccountId == null) {
            Account account = Account.builder()
                    .userId(owner.getUserId())
                    .accountNumber(accountNumber)
                    .accountType(accountType)
                    .balance(BigDecimal.ZERO)
                    .currency(currency)
                    .status(AccountStatus.ACTIVE)
                    .build();
            Account saved = accountRepository.save(account);
            return AccountMapper.toDTO(saved);
        }

        Connection conn = null;
        Account savedNewAccount;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account fundingAccount = accountRepository.findByIdForUpdate(conn, fundingAccountId)
                    .orElseThrow(() -> new AccountNotFoundException("Funding account not found: " + fundingAccountId));

            assertOwnership(fundingAccount, owner);
            assertActive(fundingAccount);

            Currency fundingCurrency = fundingAccount.getCurrency();
            BigDecimal debitAmount;

            if (fundingCurrency == currency) {
                debitAmount = deposit;
            } else {
                Map<String, BigDecimal> rates = (liveCurrencyService != null)
                        ? liveCurrencyService.getRates()
                        : CurrencyConverter.getFallbackRates();
                debitAmount = CurrencyConverter.convert(deposit, currency.name(), fundingCurrency.name(), rates);
            }

            if (fundingAccount.getBalance().compareTo(debitAmount) < 0) {
                throw new InsufficientBalanceException("Insufficient balance in funding account: available "
                        + fundingAccount.getBalance() + " " + fundingCurrency + ", required " + debitAmount + " " + fundingCurrency);
            }

            fundingAccount.setBalance(fundingAccount.getBalance().subtract(debitAmount));
            accountRepository.updateWithConnection(conn, fundingAccount);

            Account newAccount = Account.builder()
                    .userId(owner.getUserId())
                    .accountNumber(accountNumber)
                    .accountType(accountType)
                    .balance(deposit)
                    .currency(currency)
                    .status(AccountStatus.ACTIVE)
                    .build();
            savedNewAccount = accountRepository.saveWithConnection(conn, newAccount);

            Transaction debitTxn = Transaction.builder()
                    .accountId(fundingAccount.getAccountId())
                    .relatedAccountId(savedNewAccount.getAccountId())
                    .transactionType(TransactionType.TRANSFER)
                    .amount(debitAmount)
                    .currency(fundingCurrency)
                    .description("Initial funding for new account " + savedNewAccount.getAccountNumber())
                    .status(TransactionStatus.COMPLETED)
                    .build();
            transactionRepository.saveWithConnection(conn, debitTxn);

            Transaction creditTxn = Transaction.builder()
                    .accountId(savedNewAccount.getAccountId())
                    .relatedAccountId(fundingAccount.getAccountId())
                    .transactionType(TransactionType.DEPOSIT)
                    .amount(deposit)
                    .currency(currency)
                    .description("Initial opening deposit from " + fundingAccount.getAccountNumber())
                    .status(TransactionStatus.COMPLETED)
                    .build();
            transactionRepository.saveWithConnection(conn, creditTxn);

            conn.commit();
        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(conn);
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Account creation and funding failed", e);
        } finally {
            closeQuietly(conn);
        }

        return AccountMapper.toDTO(savedNewAccount);
    }

    public Account getAccountById(Long accountId, User requestingUser) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        assertOwnership(account, requestingUser);
        return account;
    }

    public Optional<Account> getAccountById(Long accountId) {
        return accountRepository.findById(accountId);
    }

    public List<AccountDTO> getAccountsForUser(User user) {
        List<Account> accounts = accountRepository.findByUserId(user.getUserId());
        return AccountMapper.toDTOList(accounts);
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

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Long firstLockId = Math.min(senderAccountId, receiverAccountId);
        Long secondLockId = Math.max(senderAccountId, receiverAccountId);

        Connection conn = null;
        Transaction resultTransaction;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account firstLocked = accountRepository.findByIdForUpdate(conn, firstLockId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstLockId));
            Account secondLocked = accountRepository.findByIdForUpdate(conn, secondLockId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondLockId));

            Account sender = senderAccountId.equals(firstLockId) ? firstLocked : secondLocked;
            Account receiver = senderAccountId.equals(firstLockId) ? secondLocked : firstLocked;

            assertOwnership(sender, requestingUser);
            assertActive(sender);
            assertActive(receiver);

            Currency senderCurrency = sender.getCurrency();
            Currency receiverCurrency = receiver.getCurrency();
            Currency effectiveCurrency = resolveCurrency(requestCurrency, senderCurrency);

            BigDecimal debitAmount = amount;
            BigDecimal creditAmount;
            BigDecimal exchangeRate = BigDecimal.ONE;
            boolean isCrossCurrency = senderCurrency != receiverCurrency;

            if (!isCrossCurrency) {
                creditAmount = debitAmount;
            } else {
                Map<String, BigDecimal> rates = (liveCurrencyService != null)
                        ? liveCurrencyService.getRates()
                        : CurrencyConverter.getFallbackRates();
                creditAmount = CurrencyConverter.convert(debitAmount, senderCurrency.name(), receiverCurrency.name(), rates);
                exchangeRate = CurrencyConverter.getExchangeRate(senderCurrency.name(), receiverCurrency.name(), rates);
            }

            if (sender.getBalance().compareTo(debitAmount) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient balance: available " + sender.getBalance() + ", requested " + debitAmount);
            }

            sender.setBalance(sender.getBalance().subtract(debitAmount));
            receiver.setBalance(receiver.getBalance().add(creditAmount));

            accountRepository.updateWithConnection(conn, sender);
            accountRepository.updateWithConnection(conn, receiver);

            String effectiveDescription = description;
            if (isCrossCurrency) {
                String conversionNote = String.format(" [Exchanged %s %s -> %s %s @ 1 %s = %s %s]",
                        debitAmount.setScale(2, RoundingMode.HALF_UP).toPlainString(), senderCurrency,
                        creditAmount.setScale(2, RoundingMode.HALF_UP).toPlainString(), receiverCurrency,
                        senderCurrency, exchangeRate.setScale(4, RoundingMode.HALF_UP).toPlainString(), receiverCurrency);
                effectiveDescription = (description != null && !description.isBlank())
                        ? (description + conversionNote)
                        : ("Cross-currency transfer" + conversionNote);
                if (effectiveDescription.length() > 255) {
                    effectiveDescription = effectiveDescription.substring(0, 255);
                }
            }

            Transaction transaction = Transaction.builder()
                    .accountId(sender.getAccountId())
                    .relatedAccountId(receiver.getAccountId())
                    .transactionType(TransactionType.TRANSFER)
                    .amount(debitAmount)
                    .currency(effectiveCurrency)
                    .description(effectiveDescription)
                    .status(TransactionStatus.COMPLETED)
                    .idempotencyKey(idempotencyKey)
                    .build();

            transaction = transactionRepository.saveWithConnection(conn, transaction);

            conn.commit();
            resultTransaction = transaction;

        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(conn);

            if (isUniqueViolation(e)) {
                return transactionRepository.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new RuntimeException("Transfer failed and could not recover", e));
            }

            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Transfer failed", e);
        } finally {
            closeQuietly(conn);
        }

        // Fraud detection runs AFTER the transfer has already committed
        // successfully. It never blocks or reverses the transfer (Option A).
        // A failure here should not break the customer-facing transfer flow,
        // so it's wrapped defensively.
        try {
            fraudDetectionService.evaluateTransfer(resultTransaction, requestingUser.getUserId());
        } catch (RuntimeException e) {
            // In Phase 25 this will use proper logging instead of println
            System.err.println("Fraud detection failed (non-fatal): " + e.getMessage());
        }

        return resultTransaction;
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