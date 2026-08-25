package com.bank.repository;

import com.bank.database.DatabaseConnection;
import com.bank.enums.BudgetPeriod;
import com.bank.enums.BudgetStatus;
import com.bank.model.Budget;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BudgetRepositoryImpl implements BudgetRepository {

    @Override
    public Optional<Budget> findById(Long budgetId) {
        String sql = "SELECT * FROM budgets WHERE budget_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, budgetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding budget by id: " + budgetId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Budget> findByUserId(Long userId) {
        String sql = "SELECT * FROM budgets WHERE user_id = ? ORDER BY budget_id";
        List<Budget> budgets = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) budgets.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding budgets for user: " + userId, e);
        }
        return budgets;
    }

    @Override
    public List<Budget> findAll() {
        String sql = "SELECT * FROM budgets ORDER BY budget_id";
        List<Budget> budgets = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) budgets.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all budgets", e);
        }
        return budgets;
    }

    @Override
    public Budget save(Budget budget) {
        return budget.getBudgetId() == null ? insert(budget) : update(budget);
    }

    private Budget insert(Budget budget) {
        String sql = """
            INSERT INTO budgets (user_id, category_id, amount_limit, period, start_date, end_date, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING budget_id
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setLong(1, budget.getUserId());
            stmt.setLong(2, budget.getCategoryId());
            stmt.setBigDecimal(3, budget.getAmountLimit());
            stmt.setString(4, budget.getPeriod().name());
            stmt.setDate(5, Date.valueOf(budget.getStartDate()));
            stmt.setDate(6, budget.getEndDate() != null ? Date.valueOf(budget.getEndDate()) : null);
            stmt.setString(7, budget.getStatus().name());
            stmt.setTimestamp(8, Timestamp.valueOf(now));
            stmt.setTimestamp(9, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    budget.setBudgetId(rs.getLong("budget_id"));
                    budget.setCreatedAt(now);
                    budget.setUpdatedAt(now);
                }
            }
            return budget;
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting budget", e);
        }
    }

    private Budget update(Budget budget) {
        String sql = """
            UPDATE budgets
            SET amount_limit = ?, period = ?, start_date = ?, end_date = ?, status = ?, updated_at = ?
            WHERE budget_id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setBigDecimal(1, budget.getAmountLimit());
            stmt.setString(2, budget.getPeriod().name());
            stmt.setDate(3, Date.valueOf(budget.getStartDate()));
            stmt.setDate(4, budget.getEndDate() != null ? Date.valueOf(budget.getEndDate()) : null);
            stmt.setString(5, budget.getStatus().name());
            stmt.setTimestamp(6, Timestamp.valueOf(now));
            stmt.setLong(7, budget.getBudgetId());

            stmt.executeUpdate();
            budget.setUpdatedAt(now);
            return budget;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating budget", e);
        }
    }

    @Override
    public boolean deleteById(Long budgetId) {
        String sql = "DELETE FROM budgets WHERE budget_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, budgetId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting budget: " + budgetId, e);
        }
    }

    private Budget mapRow(ResultSet rs) throws SQLException {
        Date endDate = rs.getDate("end_date");
        return Budget.builder()
                .budgetId(rs.getLong("budget_id"))
                .userId(rs.getLong("user_id"))
                .categoryId(rs.getLong("category_id"))
                .amountLimit(rs.getBigDecimal("amount_limit"))
                .period(BudgetPeriod.valueOf(rs.getString("period")))
                .startDate(rs.getDate("start_date").toLocalDate())
                .endDate(endDate != null ? endDate.toLocalDate() : null)
                .status(BudgetStatus.valueOf(rs.getString("status")))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}