package com.bank;

import com.bank.database.DatabaseConnection;
import com.bank.enums.AccountType;
import com.bank.enums.Currency;
import com.bank.exception.AuthenticationException;
import com.bank.exception.CurrencyMismatchException;
import com.bank.exception.InsufficientBalanceException;
import com.bank.exception.InvalidAmountException;
import com.bank.model.Account;
import com.bank.model.Category;
import com.bank.model.Transaction;
import com.bank.model.User;
import com.bank.repository.*;
import com.bank.security.SessionManager;
import com.bank.service.AccountService;
import com.bank.service.AuthService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Enterprise Banking System");
        System.out.println("  Phase 8 - Deposit (ACID + Currency)");
        System.out.println("========================================");

        // ---------------------------------------------------
        // Phase 3 - Connection sanity check
        // ---------------------------------------------------
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

        // ---------------------------------------------------
        // Phase 5 - Repository smoke tests
        // ---------------------------------------------------
        CategoryRepository catRepo = new CategoryRepositoryImpl();
        List<Category> categories = catRepo.findAll();
        System.out.println("Categories found: " + categories.size());

        UserRepository userRepo = new UserRepositoryImpl();
        System.out.println("Users found: " + userRepo.findAll().size());

        LoanRepository loanRepo = new LoanRepositoryImpl();
        System.out.println("Loans found: " + loanRepo.findAll().size());

        BudgetRepository budgetRepo = new BudgetRepositoryImpl();
        System.out.println("Budgets found: " + budgetRepo.findAll().size());

        // ---------------------------------------------------
        // Phase 6 - Authentication (idempotent test)
        // ---------------------------------------------------
        AuthService authService = new AuthService(userRepo);

        User loggedIn;
        Optional<User> existing = userRepo.findByUsername("johndoe");
        if (existing.isEmpty()) {
            User newUser = authService.register(
                    "johndoe", "john@example.com", "SecurePass123", "John Doe", "0123456789");
            System.out.println("Registered: " + newUser.getUsername() + " (id=" + newUser.getUserId() + ")");
        }
        loggedIn = authService.login("johndoe", "SecurePass123");
        System.out.println("Login success: " + loggedIn.getFullName());
        System.out.println("Session active: " + SessionManager.isUserLoggedIn());

        try {
            authService.login("johndoe", "wrongpassword");
        } catch (AuthenticationException e) {
            System.out.println("Correctly rejected: " + e.getMessage());
        }

        // ---------------------------------------------------
        // Phase 7/8 - Account creation + Deposit
        // ---------------------------------------------------
        AccountRepository accountRepo = new AccountRepositoryImpl();
        TransactionRepository transactionRepo = new TransactionRepositoryImpl();
        AccountService accountService = new AccountService(accountRepo, transactionRepo);

        Account account = accountService.createAccount(loggedIn, AccountType.SAVINGS, Currency.USD);
        System.out.println("Created account: " + account.getAccountNumber()
                + " | Currency: " + account.getCurrency()
                + " | Balance: " + account.getBalance());

        List<Account> myAccounts = accountService.getAccountsForUser(loggedIn);
        System.out.println("Accounts for user: " + myAccounts.size());

        // Successful deposit
        Transaction depositTxn = accountService.deposit(
                account.getAccountId(),
                new BigDecimal("500.00"),
                Currency.USD,
                "Initial deposit",
                loggedIn
        );
        System.out.println("Deposit successful: " + depositTxn.getAmount() + " " + depositTxn.getCurrency());

        BigDecimal newBalance = accountService.getBalance(account.getAccountId(), loggedIn);
        System.out.println("New balance: " + newBalance);

        // Deposit with null currency (should default to account's currency)
        Transaction defaultCurrencyTxn = accountService.deposit(
                account.getAccountId(),
                new BigDecimal("100.00"),
                null,
                "Deposit with default currency",
                loggedIn
        );
        System.out.println("Default-currency deposit: " + defaultCurrencyTxn.getAmount()
                + " " + defaultCurrencyTxn.getCurrency());

        // Currency mismatch (should fail)
        try {
            accountService.deposit(account.getAccountId(), new BigDecimal("100"), Currency.KHR, "test", loggedIn);
        } catch (CurrencyMismatchException e) {
            System.out.println("Correctly rejected: " + e.getMessage());
        }

        // Invalid amount (should fail)
        try {
            accountService.deposit(account.getAccountId(), new BigDecimal("-50"), null, "test", loggedIn);
        } catch (InvalidAmountException e) {
            System.out.println("Correctly rejected: " + e.getMessage());
        }

        BigDecimal finalBalance = accountService.getBalance(account.getAccountId(), loggedIn);
        System.out.println("Final balance: " + finalBalance);

        // ការដកប្រាក់ជោគជ័យ
        Transaction withdrawTxn = accountService.withdraw(
                account.getAccountId(),
                new BigDecimal("200.00"),
                Currency.USD,
                "ATM withdrawal",
                loggedIn
        );
        System.out.println("Withdrawal successful: " + withdrawTxn.getAmount() + " " + withdrawTxn.getCurrency());
        System.out.println("Balance after withdrawal: " + accountService.getBalance(account.getAccountId(), loggedIn));

// Balance មិនគ្រប់គ្រាន់ (គួរតែបរាជ័យ)
        try {
            accountService.withdraw(account.getAccountId(), new BigDecimal("999999"), null, "test", loggedIn);
        } catch (InsufficientBalanceException e) {
            System.out.println("Correctly rejected: " + e.getMessage());
        }

// Amount មិនត្រឹមត្រូវ (គួរតែបរាជ័យ)
        try {
            accountService.withdraw(account.getAccountId(), BigDecimal.ZERO, null, "test", loggedIn);
        } catch (InvalidAmountException e) {
            System.out.println("Correctly rejected: " + e.getMessage());
        }

        // ---------------------------------------------------
        // Logout
        // ---------------------------------------------------
        authService.logout();
        System.out.println("Session after logout: " + SessionManager.isUserLoggedIn());

        // ---------------------------------------------------
        // Pool shutdown MUST be the last thing that happens
        // ---------------------------------------------------
        DatabaseConnection.closePool();
    }
}