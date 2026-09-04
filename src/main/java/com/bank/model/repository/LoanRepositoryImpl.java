package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.enums.LoanStatus;
import com.bank.model.enums.RiskLevel;
import com.bank.model.entity.Loan;

import java.math.BigDecimal;
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
        String sql = "SELECT * FROM loans WHERE status = ? ORDER BY created_at DESC";
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
        try (Connection conn = DatabaseConnection.getConnection()) {
            return saveWithConnection(conn, loan);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving loan application", e);
        }
    }

    @Override
    public Loan saveWithConnection(Connection conn, Loan loan) throws SQLException {
        String sql = """
            INSERT INTO loans (user_id, account_id, requested_amount, approved_amount, interest_rate,
                               term_months, monthly_income, monthly_expense, existing_debt, credit_score,
                               risk_score, risk_level, status, outstanding_balance, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING loan_id
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();

            stmt.setLong(1, loan.getUserId());
            if (loan.getAccountId() != null) stmt.setLong(2, loan.getAccountId());
            else stmt.setNull(2, Types.BIGINT);

            stmt.setBigDecimal(3, loan.getRequestedAmount());
            if (loan.getApprovedAmount() != null) stmt.setBigDecimal(4, loan.getApprovedAmount());
            else stmt.setNull(4, Types.NUMERIC);

            stmt.setBigDecimal(5, loan.getInterestRate() != null ? loan.getInterestRate() : BigDecimal.ZERO);
            stmt.setInt(6, loan.getTermMonths());
            stmt.setBigDecimal(7, loan.getMonthlyIncome());
            stmt.setBigDecimal(8, loan.getMonthlyExpense());
            stmt.setBigDecimal(9, loan.getExistingDebt() != null ? loan.getExistingDebt() : BigDecimal.ZERO);

            if (loan.getCreditScore() != null) stmt.setInt(10, loan.getCreditScore());
            else stmt.setNull(10, Types.INTEGER);

            stmt.setBigDecimal(11, loan.getRiskScore());
            stmt.setString(12, loan.getRiskLevel() != null ? loan.getRiskLevel().name() : null);
            stmt.setString(13, loan.getStatus() != null ? loan.getStatus().name() : LoanStatus.PENDING.name());
            stmt.setBigDecimal(14, loan.getOutstandingBalance() != null ? loan.getOutstandingBalance() : BigDecimal.ZERO);
            stmt.setTimestamp(15, Timestamp.valueOf(now));
            stmt.setTimestamp(16, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    loan.setLoanId(rs.getLong("loan_id"));
                    loan.setCreatedAt(now);
                    loan.setUpdatedAt(now);
                }
            }
            return loan;
        }
    }

    @Override
    public boolean deleteById(Long loanId) {
        String sql = "UPDATE loans SET status = 'REJECTED', updated_at = CURRENT_TIMESTAMP WHERE loan_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, loanId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting/cancelling loan: " + loanId, e);
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
            UPDATE loans SET
                account_id = ?, approved_amount = ?, interest_rate = ?, term_months = ?,
                status = ?, approved_by = ?, approved_at = ?, rejection_reason = ?,
                outstanding_balance = ?, updated_at = ?
            WHERE loan_id = ?
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (loan.getAccountId() != null) stmt.setLong(1, loan.getAccountId());
            else stmt.setNull(1, Types.BIGINT);

            stmt.setBigDecimal(2, loan.getApprovedAmount());
            stmt.setBigDecimal(3, loan.getInterestRate());
            stmt.setInt(4, loan.getTermMonths());
            stmt.setString(5, loan.getStatus().name());

            if (loan.getApprovedBy() != null) stmt.setLong(6, loan.getApprovedBy());
            else stmt.setNull(6, Types.BIGINT);

            if (loan.getApprovedAt() != null) stmt.setTimestamp(7, Timestamp.valueOf(loan.getApprovedAt()));
            else stmt.setNull(7, Types.TIMESTAMP);

            stmt.setString(8, loan.getRejectionReason());
            stmt.setBigDecimal(9, loan.getOutstandingBalance());
            stmt.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(11, loan.getLoanId());

            stmt.executeUpdate();
        }
    }

    private Loan mapRow(ResultSet rs) throws SQLException {
        Long accountId = rs.getObject("account_id") != null ? rs.getLong("account_id") : null;
        Long approvedBy = rs.getObject("approved_by") != null ? rs.getLong("approved_by") : null;
        Integer creditScoreVal = rs.getObject("credit_score") != null ? rs.getInt("credit_score") : null;
        Timestamp approvedAtTs = rs.getTimestamp("approved_at");

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
                .creditScore(creditScoreVal)
                .riskScore(rs.getBigDecimal("risk_score"))
                .riskLevel(rs.getString("risk_level") != null ? RiskLevel.valueOf(rs.getString("risk_level")) : null)
                .status(LoanStatus.valueOf(rs.getString("status")))
                .approvedBy(approvedBy)
                .approvedAt(approvedAtTs != null ? approvedAtTs.toLocalDateTime() : null)
                .rejectionReason(rs.getString("rejection_reason"))
                .outstandingBalance(rs.getBigDecimal("outstanding_balance"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}