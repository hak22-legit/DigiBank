package com.bank.repository;

import com.bank.database.DatabaseConnection;
import com.bank.enums.LoanStatus;
import com.bank.enums.RiskLevel;
import com.bank.model.Loan;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LoanRepositoryImpl implements LoanRepository {

    @Override
    public Optional<Loan> findById(Long loanId) {
        String sql = "SELECT * FROM loans WHERE loan_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, loanId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding loan by id: " + loanId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Loan> findByUserId(Long userId) {
        String sql = "SELECT * FROM loans WHERE user_id = ? ORDER BY created_at DESC";
        List<Loan> loans = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) loans.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding loans for user: " + userId, e);
        }
        return loans;
    }

    @Override
    public List<Loan> findByStatus(String status) {
        String sql = "SELECT * FROM loans WHERE status = ? ORDER BY created_at";
        List<Loan> loans = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) loans.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding loans by status: " + status, e);
        }
        return loans;
    }

    @Override
    public List<Loan> findAll() {
        String sql = "SELECT * FROM loans ORDER BY created_at DESC";
        List<Loan> loans = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) loans.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all loans", e);
        }
        return loans;
    }

    @Override
    public Loan save(Loan loan) {
        return loan.getLoanId() == null ? insert(loan) : update(loan);
    }

    private Loan insert(Loan loan) {
        String sql = """
            INSERT INTO loans (user_id, account_id, requested_amount, approved_amount, interest_rate,
                                term_months, monthly_income, monthly_expense, existing_debt, credit_score,
                                risk_score, risk_level, status, approved_by, approved_at, rejection_reason,
                                outstanding_balance, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING loan_id
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setLong(1, loan.getUserId());
            setNullableLong(stmt, 2, loan.getAccountId());
            stmt.setBigDecimal(3, loan.getRequestedAmount());
            stmt.setBigDecimal(4, loan.getApprovedAmount());
            stmt.setBigDecimal(5, loan.getInterestRate());
            stmt.setInt(6, loan.getTermMonths());
            stmt.setBigDecimal(7, loan.getMonthlyIncome());
            stmt.setBigDecimal(8, loan.getMonthlyExpense());
            stmt.setBigDecimal(9, loan.getExistingDebt());
            setNullableInt(stmt, 10, loan.getCreditScore());
            stmt.setBigDecimal(11, loan.getRiskScore());
            stmt.setString(12, loan.getRiskLevel() != null ? loan.getRiskLevel().name() : null);
            stmt.setString(13, loan.getStatus().name());
            setNullableLong(stmt, 14, loan.getApprovedBy());
            stmt.setTimestamp(15, loan.getApprovedAt() != null ? Timestamp.valueOf(loan.getApprovedAt()) : null);
            stmt.setString(16, loan.getRejectionReason());
            stmt.setBigDecimal(17, loan.getOutstandingBalance());
            stmt.setTimestamp(18, Timestamp.valueOf(now));
            stmt.setTimestamp(19, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    loan.setLoanId(rs.getLong("loan_id"));
                    loan.setCreatedAt(now);
                    loan.setUpdatedAt(now);
                }
            }
            return loan;
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting loan", e);
        }
    }

    private Loan update(Loan loan) {
        String sql = """
            UPDATE loans
            SET approved_amount = ?, interest_rate = ?, risk_score = ?, risk_level = ?,
                status = ?, approved_by = ?, approved_at = ?, rejection_reason = ?,
                outstanding_balance = ?, updated_at = ?
            WHERE loan_id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setBigDecimal(1, loan.getApprovedAmount());
            stmt.setBigDecimal(2, loan.getInterestRate());
            stmt.setBigDecimal(3, loan.getRiskScore());
            stmt.setString(4, loan.getRiskLevel() != null ? loan.getRiskLevel().name() : null);
            stmt.setString(5, loan.getStatus().name());
            setNullableLong(stmt, 6, loan.getApprovedBy());
            stmt.setTimestamp(7, loan.getApprovedAt() != null ? Timestamp.valueOf(loan.getApprovedAt()) : null);
            stmt.setString(8, loan.getRejectionReason());
            stmt.setBigDecimal(9, loan.getOutstandingBalance());
            stmt.setTimestamp(10, Timestamp.valueOf(now));
            stmt.setLong(11, loan.getLoanId());

            stmt.executeUpdate();
            loan.setUpdatedAt(now);
            return loan;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating loan", e);
        }
    }

    @Override
    public boolean deleteById(Long loanId) {
        String sql = "DELETE FROM loans WHERE loan_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, loanId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting loan: " + loanId, e);
        }
    }

    @Override
    public Optional<Loan> findByIdForUpdate(Connection conn, Long loanId) throws SQLException {
        String sql = "SELECT * FROM loans WHERE loan_id = ? FOR UPDATE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, loanId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    @Override
    public void updateWithConnection(Connection conn, Loan loan) throws SQLException {
        String sql = """
            UPDATE loans
            SET status = ?, outstanding_balance = ?, updated_at = ?
            WHERE loan_id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            stmt.setString(1, loan.getStatus().name());
            stmt.setBigDecimal(2, loan.getOutstandingBalance());
            stmt.setTimestamp(3, Timestamp.valueOf(now));
            stmt.setLong(4, loan.getLoanId());
            stmt.executeUpdate();
            loan.setUpdatedAt(now);
        }
    }

    private void setNullableLong(PreparedStatement stmt, int index, Long value) throws SQLException {
        if (value != null) stmt.setLong(index, value);
        else stmt.setNull(index, Types.BIGINT);
    }

    private void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value != null) stmt.setInt(index, value);
        else stmt.setNull(index, Types.INTEGER);
    }

    private Loan mapRow(ResultSet rs) throws SQLException {
        Long accountId = rs.getObject("account_id") != null ? rs.getLong("account_id") : null;
        Integer creditScore = rs.getObject("credit_score") != null ? rs.getInt("credit_score") : null;
        Long approvedBy = rs.getObject("approved_by") != null ? rs.getLong("approved_by") : null;
        String riskLevelStr = rs.getString("risk_level");
        Timestamp approvedAt = rs.getTimestamp("approved_at");

        return Loan.builder()
                .loanId(rs.getLong("loan_id"))
                .userId(rs.getLong("user_id"))
                .accountId(accountId)
                .requestedAmount(rs.getBigDecimal("requested_amount"))
                .approvedAmount(rs.getBigDecimal("approved_amount"))
                .interestRate(rs.getBigDecimal("interest_rate"))
                .termMonths(rs.getInt("term_months"))
                .monthlyIncome(rs.getBigDecimal("monthly_income"))
                .monthlyExpense(rs.getBigDecimal("monthly_expense"))
                .existingDebt(rs.getBigDecimal("existing_debt"))
                .creditScore(creditScore)
                .riskScore(rs.getBigDecimal("risk_score"))
                .riskLevel(riskLevelStr != null ? RiskLevel.valueOf(riskLevelStr) : null)
                .status(LoanStatus.valueOf(rs.getString("status")))
                .approvedBy(approvedBy)
                .approvedAt(approvedAt != null ? approvedAt.toLocalDateTime() : null)
                .rejectionReason(rs.getString("rejection_reason"))
                .outstandingBalance(rs.getBigDecimal("outstanding_balance"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}