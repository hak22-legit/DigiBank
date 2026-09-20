package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.model.dto.GlobalLedgerItem;
import com.bank.model.entity.Account;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    public Account saveWithConnection(Connection conn, Account account) throws SQLException {
        if (account.getAccountId() != null) {
            updateWithConnection(conn, account);
            return account;
        }

        String sql = """
            INSERT INTO accounts (user_id, account_number, account_type, balance,
                                   currency, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING account_id
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    @Override
    public List<GlobalLedgerItem> findGlobalLedger(int offset, int limit, String search, Currency currency) {
        StringBuilder sql = new StringBuilder("""
            SELECT a.account_id, a.account_number, a.user_id, u.full_name AS owner_name,
                   a.account_type, a.currency, a.balance, a.status
            FROM accounts a
            JOIN users u ON a.user_id = u.user_id
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(a.account_number) LIKE ? OR LOWER(u.full_name) LIKE ? OR LOWER(u.username) LIKE ?)");
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }
        if (currency != null) {
            sql.append(" AND a.currency = ?");
            params.add(currency.name());
        }
        sql.append(" ORDER BY a.account_id ASC LIMIT ? OFFSET ?");
        params.add(limit > 0 ? limit : 10);
        params.add(Math.max(0, offset));

        List<GlobalLedgerItem> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.math.BigDecimal bal = rs.getBigDecimal("balance");
                    String risk = bal != null && bal.compareTo(new java.math.BigDecimal("50000")) > 0 ? "HIGH" :
                                 (bal != null && bal.compareTo(new java.math.BigDecimal("10000")) > 0 ? "MED" : "LOW");

                    list.add(GlobalLedgerItem.builder()
                            .accountId(rs.getLong("account_id"))
                            .accountNumber(rs.getString("account_number"))
                            .userId(rs.getLong("user_id"))
                            .ownerName(rs.getString("owner_name"))
                            .accountType(AccountType.valueOf(rs.getString("account_type")))
                            .currency(Currency.valueOf(rs.getString("currency")))
                            .balance(bal)
                            .riskLevel(risk)
                            .status(AccountStatus.valueOf(rs.getString("status")))
                            .build());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching global ledger accounts", e);
        }
        return list;
    }

    @Override
    public long countAccounts(String search, Currency currency) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM accounts a
            JOIN users u ON a.user_id = u.user_id
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(a.account_number) LIKE ? OR LOWER(u.full_name) LIKE ? OR LOWER(u.username) LIKE ?)");
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }
        if (currency != null) {
            sql.append(" AND a.currency = ?");
            params.add(currency.name());
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting global ledger accounts", e);
        }
    }

    @Override
    public Map<Currency, java.math.BigDecimal> getVaultTotalAssets() {
        String sql = "SELECT currency, COALESCE(SUM(balance), 0) AS total_vault FROM accounts GROUP BY currency";
        Map<Currency, java.math.BigDecimal> map = new LinkedHashMap<>();
        map.put(Currency.USD, java.math.BigDecimal.ZERO);
        map.put(Currency.KHR, java.math.BigDecimal.ZERO);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    Currency c = Currency.valueOf(rs.getString("currency"));
                    map.put(c, rs.getBigDecimal("total_vault"));
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error calculating vault total assets", e);
        }
        return map;
    }
}