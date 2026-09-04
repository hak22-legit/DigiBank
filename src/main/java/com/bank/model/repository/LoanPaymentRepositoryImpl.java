package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.enums.LoanPaymentStatus;
import com.bank.model.entity.LoanPayment;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LoanPaymentRepositoryImpl implements LoanPaymentRepository {

    @Override
    public Optional<LoanPayment> findById(Long paymentId) {
        String sql = "SELECT * FROM loan_payments WHERE payment_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, paymentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding loan payment by id: " + paymentId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<LoanPayment> findByLoanId(Long loanId) {
        String sql = "SELECT * FROM loan_payments WHERE loan_id = ? ORDER BY payment_date DESC";
        List<LoanPayment> payments = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, loanId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) payments.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding payments for loan: " + loanId, e);
        }
        return payments;
    }

    @Override
    public List<LoanPayment> findAll() {
        String sql = "SELECT * FROM loan_payments ORDER BY payment_date DESC";
        List<LoanPayment> payments = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) payments.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all loan payments", e);
        }
        return payments;
    }

    @Override
    public LoanPayment save(LoanPayment payment) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return saveWithConnection(conn, payment);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving loan payment", e);
        }
    }

    @Override
    public LoanPayment saveWithConnection(Connection conn, LoanPayment payment) throws SQLException {
        String sql = """
            INSERT INTO loan_payments (loan_id, account_id, amount, principal_amount, interest_amount,
                                        payment_date, transaction_id, due_date, status, payment_method, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING payment_id
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            stmt.setLong(1, payment.getLoanId());
            stmt.setLong(2, payment.getAccountId());
            stmt.setBigDecimal(3, payment.getAmount());
            stmt.setBigDecimal(4, payment.getPrincipalAmount());
            stmt.setBigDecimal(5, payment.getInterestAmount());
            stmt.setTimestamp(6, Timestamp.valueOf(now));
            if (payment.getTransactionId() != null) {
                stmt.setLong(7, payment.getTransactionId());
            } else {
                stmt.setNull(7, Types.BIGINT);
            }
            stmt.setDate(8, payment.getDueDate() != null ? Date.valueOf(payment.getDueDate()) : null);
            stmt.setString(9, payment.getStatus() != null ? payment.getStatus().name() : LoanPaymentStatus.COMPLETED.name());
            stmt.setString(10, payment.getPaymentMethod());
            stmt.setTimestamp(11, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    payment.setPaymentId(rs.getLong("payment_id"));
                    payment.setPaymentDate(now);
                    payment.setCreatedAt(now);
                }
            }
            return payment;
        }
    }

    @Override
    public boolean deleteById(Long paymentId) {
        String sql = "DELETE FROM loan_payments WHERE payment_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, paymentId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting loan payment: " + paymentId, e);
        }
    }

    private LoanPayment mapRow(ResultSet rs) throws SQLException {
        Long transactionId = rs.getObject("transaction_id") != null ? rs.getLong("transaction_id") : null;
        Date dueDate = rs.getDate("due_date");

        return LoanPayment.builder()
                .paymentId(rs.getLong("payment_id"))
                .loanId(rs.getLong("loan_id"))
                .accountId(rs.getLong("account_id"))
                .transactionId(transactionId)
                .amount(rs.getBigDecimal("amount"))
                .principalAmount(rs.getBigDecimal("principal_amount"))
                .interestAmount(rs.getBigDecimal("interest_amount"))
                .paymentDate(rs.getTimestamp("payment_date").toLocalDateTime())
                .dueDate(dueDate != null ? dueDate.toLocalDate() : null)
                .status(LoanPaymentStatus.valueOf(rs.getString("status")))
                .paymentMethod(rs.getString("payment_method"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }
}