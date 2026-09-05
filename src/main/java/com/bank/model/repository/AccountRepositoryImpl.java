package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.model.entity.Account;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountRepositoryImpl implements AccountRepository {

    @Override
    public Optional<Account> findById(Long accountId) {
        String sql = "SELECT * FROM accounts WHERE account_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding account by id: " + accountId, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        String sql = "SELECT * FROM accounts WHERE account_number = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, accountNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding account by number: " + accountNumber, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Account> findByUserId(Long userId) {
        String sql = "SELECT * FROM accounts WHERE user_id = ? ORDER BY account_id";
        List<Account> accounts = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) accounts.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding accounts for user: " + userId, e);
        }
        return accounts;
    }

    @Override
    public List<Account> findAll() {
        String sql = "SELECT * FROM accounts ORDER BY account_id";
        List<Account> accounts = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) accounts.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all accounts", e);
        }
        return accounts;
    }

    @Override
    public Account save(Account account) {
        return account.getAccountId() == null ? insert(account) : update(account);
    }

    private Account insert(Account account) {
        String sql = """
            INSERT INTO accounts (user_id, account_number, account_type, balance,
                                   currency, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING account_id
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setLong(1, account.getUserId());
            stmt.setString(2, account.getAccountNumber());
            stmt.setString(3, account.getAccountType().name());
            stmt.setBigDecimal(4, account.getBalance());
            stmt.setString(5, account.getCurrency().name());
            stmt.setString(6, account.getStatus().name());
            stmt.setTimestamp(7, Timestamp.valueOf(now));
            stmt.setTimestamp(8, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    account.setAccountId(rs.getLong("account_id"));
                    account.setCreatedAt(now);
                    account.setUpdatedAt(now);
                }
            }
            return account;
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting account", e);
        }
    }

    private Account update(Account account) {
        String sql = """
            UPDATE accounts
            SET balance = ?, status = ?, updated_at = ?
            WHERE account_id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setBigDecimal(1, account.getBalance());
            stmt.setString(2, account.getStatus().name());
            stmt.setTimestamp(3, Timestamp.valueOf(now));
            stmt.setLong(4, account.getAccountId());

            stmt.executeUpdate();
            account.setUpdatedAt(now);
            return account;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating account", e);
        }
    }

    @Override
    public boolean deleteById(Long accountId) {
        String sql = "DELETE FROM accounts WHERE account_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, accountId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting account: " + accountId, e);
        }
    }

    @Override
    public Optional<Account> findByIdForUpdate(Connection conn, Long accountId) throws SQLException {
        String sql = "SELECT * FROM accounts WHERE account_id = ? FOR UPDATE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    @Override
    public void updateWithConnection(Connection conn, Account account) throws SQLException {
        String sql = """
            UPDATE accounts
            SET balance = ?, status = ?, updated_at = ?
            WHERE account_id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            stmt.setBigDecimal(1, account.getBalance());
            stmt.setString(2, account.getStatus().name());
            stmt.setTimestamp(3, Timestamp.valueOf(now));
            stmt.setLong(4, account.getAccountId());
            stmt.executeUpdate();
            account.setUpdatedAt(now);
        }
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        return Account.builder()
                .accountId(rs.getLong("account_id"))
                .userId(rs.getLong("user_id"))
                .accountNumber(rs.getString("account_number"))
                .accountType(AccountType.valueOf(rs.getString("account_type")))
                .balance(rs.getBigDecimal("balance"))
                .currency(Currency.valueOf(rs.getString("currency")))
                .status(AccountStatus.valueOf(rs.getString("status")))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}