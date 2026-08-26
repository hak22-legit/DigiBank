package com.bank;

import com.bank.database.DatabaseConnection;
import com.bank.enums.AccountType;
import com.bank.enums.Currency;
import com.bank.enums.HistoryFilter;
import com.bank.exception.*;
import com.bank.model.*;
import com.bank.repository.*;
import com.bank.security.SessionManager;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.service.TransactionService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Enterprise Banking System");
        System.out.println("  Phase 9 - Withdrawal");
        System.out.println("========================================");

        // Phase 3 - connection sanity check
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM categories")) {

            if (rs.next()) {
                System.out.println("Database connection: SUCCESS");
                System.out.println("Categories in database: " + rs.getInt(1));
            }
        } catch (Exception e) {
            System.err.println("Database connection: FAILED");
            e.printStackTrace();
        }

        // Repositories
        UserRepository userRepo = new UserRepositoryImpl();
        AccountRepository accountRepo = new AccountRepositoryImpl();
        TransactionRepository transactionRepo = new TransactionRepositoryImpl();

        // Services (note the new constructor wiring)
        AccountService accountService = new AccountService(accountRepo, transactionRepo);
        AuthService authService = new AuthService(userRepo, accountService);

        // Phase 6 - Auth (idempotent test)
        User loggedIn;
        Optional<User> existing = userRepo.findByUsername("johndoe");
        if (existing.isEmpty()) {
            User newUser = authService.register(
                    "johndoe", "john@example.com", "SecurePass123", "John Doe", "0123456789");
            System.out.println("Registered: " + newUser.getUsername() + " (id=" + newUser.getUserId() + ")");
            System.out.println("Default account auto-created on registration.");
        }
        loggedIn = authService.login("johndoe", "SecurePass123");
        System.out.println("Login success: " + loggedIn.getFullName());

        // Show all accounts for this user (should include the auto-created default account)
        List<Account> myAccounts = accountService.getAccountsForUser(loggedIn);
        System.out.println("Accounts for user: " + myAccounts.size());
        for (Account acc : myAccounts) {
            System.out.println("  - " + acc.getAccountNumber() + " | " + acc.getAccountType()
                    + " | " + acc.getCurrency() + " | Balance: " + acc.getBalance());
        }

        // Use the first account (the default one) for deposit/withdraw tests
        Account primaryAccount = myAccounts.get(0);

        // Phase 8 - Deposit
        Transaction depositTxn = accountService.deposit(
                primaryAccount.getAccountId(), new BigDecimal("500.00"), Currency.USD,
                "Initial deposit", loggedIn);
        System.out.println("Deposit successful: " + depositTxn.getAmount() + " " + depositTxn.getCurrency());
        System.out.println("Balance after deposit: " + accountService.getBalance(primaryAccount.getAccountId(), loggedIn));

        // Phase 9 - Withdrawal
        Transaction withdrawTxn = accountService.withdraw(
                primaryAccount.getAccountId(), new BigDecimal("200.00"), Currency.USD,
                "ATM withdrawal", loggedIn);
        System.out.println("Withdrawal successful: " + withdrawTxn.getAmount() + " " + withdrawTxn.getCurrency());
        System.out.println("Balance after withdrawal: " + accountService.getBalance(primaryAccount.getAccountId(), loggedIn));

        try {
            accountService.withdraw(primaryAccount.getAccountId(), new BigDecimal("999999"), null, "test", loggedIn);
        } catch (InsufficientBalanceException e) {
            System.out.println("Correctly rejected: " + e.getMessage());
        }

        try {
            accountService.withdraw(primaryAccount.getAccountId(), BigDecimal.ZERO, null, "test", loggedIn);
        } catch (InvalidAmountException e) {
            System.out.println("Correctly rejected: " + e.getMessage());
        }

        Account secondAccount = accountService.createAccount(loggedIn, AccountType.SAVINGS, Currency.USD);
        System.out.println("Second account created: " + secondAccount.getAccountNumber());

        UUID transferKey = UUID.randomUUID();

        Transaction transferTxn = accountService.transfer(
                primaryAccount.getAccountId(),
                secondAccount.getAccountId(),
                new BigDecimal("100.00"),
                Currency.USD,
                "Move to savings",
                transferKey,
                loggedIn
        );
        System.out.println("Transfer successful: " + transferTxn.getAmount()
                + " from account " + transferTxn.getAccountId() + " to " + transferTxn.getRelatedAccountId());

        System.out.println("Primary balance: " + accountService.getBalance(primaryAccount.getAccountId(), loggedIn));
        System.out.println("Second balance: " + accountService.getBalance(secondAccount.getAccountId(), loggedIn));

// ព្យាយាមម្តងទៀតជាមួយ idempotency key ដដែល - មិនគួរផ្ទេរម្តងទៀតទេ
        Transaction retryTxn = accountService.transfer(
                primaryAccount.getAccountId(), secondAccount.getAccountId(),
                new BigDecimal("100.00"), Currency.USD, "Move to savings",
                transferKey, loggedIn
        );
        System.out.println("Retry returned same transaction id: " + retryTxn.getTransactionId().equals(transferTxn.getTransactionId()));
        System.out.println("Primary balance after retry (should be unchanged): " + accountService.getBalance(primaryAccount.getAccountId(), loggedIn));

// ផ្ទេរទៅ account ដដែល (គួរបរាជ័យ)
        try {
            accountService.transfer(primaryAccount.getAccountId(), primaryAccount.getAccountId(),
                    new BigDecimal("10"), Currency.USD, "test", UUID.randomUUID(), loggedIn);
        } catch (InvalidTransferException e) {
            System.out.println("Correctly rejected: " + e.getMessage());
        }

// គ្មាន idempotency key (គួរបរាជ័យ)
        try {
            accountService.transfer(primaryAccount.getAccountId(), secondAccount.getAccountId(),
                    new BigDecimal("10"), Currency.USD, "test", null, loggedIn);
        } catch (InvalidTransferException e) {
            System.out.println("Correctly rejected: " + e.getMessage());
        }

        TransactionService transactionService = new TransactionService(transactionRepo, accountRepo);

        List<TransactionView> allHistory = transactionService.getTransactionHistory(
                primaryAccount.getAccountId(), HistoryFilter.ALL, loggedIn);
        System.out.println("All transactions for primary account: " + allHistory.size());
        for (TransactionView v : allHistory) {
            System.out.println("  [" + v.getDirection() + "] " + v.getTransaction().getTransactionType()
                    + " " + v.getTransaction().getAmount() + " " + v.getTransaction().getCurrency());
        }

        List<TransactionView> incomeOnly = transactionService.getTransactionHistory(
                primaryAccount.getAccountId(), HistoryFilter.INCOME, loggedIn);
        System.out.println("Income only: " + incomeOnly.size());

        List<TransactionView> outcomeOnly = transactionService.getTransactionHistory(
                primaryAccount.getAccountId(), HistoryFilter.OUTCOME, loggedIn);
        System.out.println("Outcome only: " + outcomeOnly.size());

        authService.logout();
        System.out.println("Session after logout: " + SessionManager.isUserLoggedIn());

        DatabaseConnection.closePool();
    }
}