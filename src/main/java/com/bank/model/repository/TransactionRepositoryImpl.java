package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionStatus;
import com.bank.model.enums.TransactionType;
import com.bank.model.entity.Transaction;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TransactionRepositoryImpl implements TransactionRepository {

    @Override
    public Optional<Transaction> findById(Long transactionId) {
        String sql = "SELECT * FROM transactions WHERE transaction_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding transaction by id: " + transactionId, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey) {
        String sql = "SELECT * FROM transactions WHERE idempotency_key = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, idempotencyKey);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding transaction by idempotency key", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Transaction> findHistoryForAccount(Long accountId) {
        String sql = """
        SELECT * FROM transactions
        WHERE account_id = ? OR related_account_id = ?
        ORDER BY transaction_date DESC
        """;
        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, accountId);
            stmt.setLong(2, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) transactions.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding transaction history for account: " + accountId, e);
        }
        return transactions;
    }

    @Override
    public List<Transaction> findByAccountId(Long accountId) {
        String sql = "SELECT * FROM transactions WHERE account_id = ? ORDER BY transaction_date DESC";
        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) transactions.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding transactions for account: " + accountId, e);
        }
        return transactions;
    }

    @Override
    public List<Transaction> findByAccountIdAndDateRange(Long accountId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT * FROM transactions
            WHERE account_id = ? AND transaction_date BETWEEN ? AND ?
            ORDER BY transaction_date DESC
            """;
        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, accountId);
            stmt.setTimestamp(2, Timestamp.valueOf(from));
            stmt.setTimestamp(3, Timestamp.valueOf(to));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) transactions.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding transactions by date range", e);
        }
        return transactions;
    }

    @Override
    public List<Transaction> findAll() {
        String sql = "SELECT * FROM transactions ORDER BY transaction_date DESC";
        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) transactions.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all transactions", e);
        }
        return transactions;
    }

    @Override
    public Transaction save(Transaction transaction) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return saveWithConnection(conn, transaction);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving transaction", e);
        }
    }

    @Override
    public Transaction saveWithConnection(Connection conn, Transaction transaction) throws SQLException {
        String sql = """
            INSERT INTO transactions (account_id, related_account_id, category_id, transaction_type,
                                       amount, currency, description, status, idempotency_key,
                                       transaction_date, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING transaction_id
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();

            UUID key = transaction.getIdempotencyKey() != null
                    ? transaction.getIdempotencyKey()
                    : UUID.randomUUID();
            transaction.setIdempotencyKey(key);

            stmt.setLong(1, transaction.getAccountId());
            if (transaction.getRelatedAccountId() != null) {
                stmt.setLong(2, transaction.getRelatedAccountId());
            } else {
                stmt.setNull(2, Types.BIGINT);
            }
            if (transaction.getCategoryId() != null) {
                stmt.setLong(3, transaction.getCategoryId());
            } else {
                stmt.setNull(3, Types.BIGINT);
            }
            stmt.setString(4, transaction.getTransactionType().name());
            stmt.setBigDecimal(5, transaction.getAmount());
            stmt.setString(6, transaction.getCurrency().name());
            stmt.setString(7, transaction.getDescription());
            stmt.setString(8, transaction.getStatus().name());
            stmt.setObject(9, key);
            stmt.setTimestamp(10, Timestamp.valueOf(now));
            stmt.setTimestamp(11, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    transaction.setTransactionId(rs.getLong("transaction_id"));
                    transaction.setTransactionDate(now);
                    transaction.setCreatedAt(now);
                }
            }
            return transaction;
        }
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        Long relatedAccountId = rs.getObject("related_account_id") != null
                ? rs.getLong("related_account_id") : null;
        Long categoryId = rs.getObject("category_id") != null
                ? rs.getLong("category_id") : null;

        return Transaction.builder()
                .transactionId(rs.getLong("transaction_id"))
                .accountId(rs.getLong("account_id"))
                .relatedAccountId(relatedAccountId)
                .categoryId(categoryId)
                .transactionType(TransactionType.valueOf(rs.getString("transaction_type")))
                .amount(rs.getBigDecimal("amount"))
                .currency(Currency.valueOf(rs.getString("currency")))
                .description(rs.getString("description"))
                .status(TransactionStatus.valueOf(rs.getString("status")))
                .idempotencyKey((UUID) rs.getObject("idempotency_key"))
                .transactionDate(rs.getTimestamp("transaction_date").toLocalDateTime())
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }
}