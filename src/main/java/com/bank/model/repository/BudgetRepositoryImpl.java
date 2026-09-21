package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.enums.BudgetPeriod;
import com.bank.model.enums.BudgetStatus;
import com.bank.model.entity.Budget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BudgetRepositoryImpl implements BudgetRepository {
    private static final Logger logger = LoggerFactory.getLogger(BudgetRepositoryImpl.class);

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
            logger.error("Error finding budget by id: {}", budgetId, e);
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
            logger.error("Error finding budgets for user: {}", userId, e);
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
            logger.error("Error finding all budgets", e);
            throw new RuntimeException("Error finding all budgets", e);
        }
        return budgets;
    }

    @Override
    public Optional<Budget> findByUserAndCategoryAndPeriodAndStartDate(Long userId, Long categoryId, BudgetPeriod period, LocalDate startDate) {
        String sql = """
            SELECT * FROM budgets
            WHERE user_id = ? AND category_id = ? AND period = ? AND start_date = ?
            LIMIT 1
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, categoryId);
            stmt.setString(3, period != null ? period.name() : BudgetPeriod.MONTHLY.name());
            stmt.setDate(4, Date.valueOf(startDate));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding budget for user {} category {} period {} start_date {}: {}", userId, categoryId, period, startDate, e.getMessage(), e);
            throw new RuntimeException("Error finding budget: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Budget save(Budget budget) {
        if (budget.getBudgetId() != null) {
            return update(budget);
        }

        // Two-step check: Check if a record exists for (user_id, category_id, period, start_date)
        String checkSql = """
            SELECT budget_id FROM budgets
            WHERE user_id = ? AND category_id = ? AND period = ? AND start_date = ?
            LIMIT 1
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkSql)) {

            stmt.setLong(1, budget.getUserId());
            stmt.setLong(2, budget.getCategoryId());
            stmt.setString(3, budget.getPeriod() != null ? budget.getPeriod().name() : BudgetPeriod.MONTHLY.name());
            stmt.setDate(4, Date.valueOf(budget.getStartDate()));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    budget.setBudgetId(rs.getLong("budget_id"));
                    return update(budget);
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking existing budget before save: {}", e.getMessage(), e);
            throw new RuntimeException("Error checking existing budget: " + e.getMessage(), e);
        }

        return insert(budget);
    }

    private Budget insert(Budget budget) {
        String sql = """
            INSERT INTO budgets (user_id, category_id, amount_limit, period, start_date, end_date, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (user_id, category_id, period, start_date)
            DO UPDATE SET amount_limit = EXCLUDED.amount_limit,
                          end_date = EXCLUDED.end_date,
                          status = EXCLUDED.status,
                          updated_at = NOW()
            RETURNING budget_id, created_at, updated_at
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, budget.getUserId());
            stmt.setLong(2, budget.getCategoryId());
            stmt.setBigDecimal(3, budget.getAmountLimit());
            stmt.setString(4, budget.getPeriod() != null ? budget.getPeriod().name() : BudgetPeriod.MONTHLY.name());
            stmt.setDate(5, Date.valueOf(budget.getStartDate()));
            stmt.setDate(6, budget.getEndDate() != null ? Date.valueOf(budget.getEndDate()) : null);
            stmt.setString(7, budget.getStatus() != null ? budget.getStatus().name() : BudgetStatus.ACTIVE.name());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    budget.setBudgetId(rs.getLong("budget_id"));
                    Timestamp cat = rs.getTimestamp("created_at");
                    if (cat != null) {
                        budget.setCreatedAt(cat.toLocalDateTime());
                    } else {
                        budget.setCreatedAt(LocalDateTime.now());
                    }
                    Timestamp uat = rs.getTimestamp("updated_at");
                    if (uat != null) {
                        budget.setUpdatedAt(uat.toLocalDateTime());
                    } else {
                        budget.setUpdatedAt(LocalDateTime.now());
                    }
                }
            }
            return budget;
        } catch (SQLException e) {
            logger.error("Database error inserting/upserting budget: {}", e.getMessage(), e);
            throw new RuntimeException("Error inserting budget: " + e.getMessage(), e);
        }
    }

    private Budget update(Budget budget) {
        String sql = """
            UPDATE budgets
            SET amount_limit = ?, period = ?, start_date = ?, end_date = ?, status = ?, updated_at = NOW()
            WHERE budget_id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBigDecimal(1, budget.getAmountLimit());
            stmt.setString(2, budget.getPeriod() != null ? budget.getPeriod().name() : BudgetPeriod.MONTHLY.name());
            stmt.setDate(3, Date.valueOf(budget.getStartDate()));
            stmt.setDate(4, budget.getEndDate() != null ? Date.valueOf(budget.getEndDate()) : null);
            stmt.setString(5, budget.getStatus() != null ? budget.getStatus().name() : BudgetStatus.ACTIVE.name());
            stmt.setLong(6, budget.getBudgetId());

            stmt.executeUpdate();
            budget.setUpdatedAt(LocalDateTime.now());
            return budget;
        } catch (SQLException e) {
            logger.error("Database error updating budget {}: {}", budget.getBudgetId(), e.getMessage(), e);
            throw new RuntimeException("Error updating budget: " + e.getMessage(), e);
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
            logger.error("Database error deleting budget {}: {}", budgetId, e.getMessage(), e);
            throw new RuntimeException("Error deleting budget: " + budgetId, e);
        }
    }

    @Override
    public Long upsertCategoryAndBudgetLimit(Long userId, String categoryName, java.math.BigDecimal monthlyCap, int month, int year) {
        return upsertCategoryAndBudgetLimit(userId, null, categoryName, monthlyCap, month, year);
    }

    @Override
    public Long upsertCategoryAndBudgetLimit(Long userId, Long knownCategoryId, String categoryName, java.math.BigDecimal monthlyCap, int month, int year) {
        int targetMonth = month > 0 ? month : 9;
        int targetYear = year > 0 ? year : 2026;
        String trimmedName = (categoryName != null) ? categoryName.trim() : "";

        String checkCatSql = "SELECT category_id AS id FROM categories WHERE (user_id = ? OR (user_id IS NULL AND is_system = true)) AND LOWER(name) = LOWER(?) ORDER BY is_system ASC LIMIT 1";

        String checkBudgetSql = """
            SELECT budget_id AS id FROM budgets
            WHERE user_id = ? AND category_id = ?
              AND (period = 'MONTHLY' OR period IS NULL)
              AND (start_date = ? OR (EXTRACT(MONTH FROM start_date) = ? AND EXTRACT(YEAR FROM start_date) = ?))
            LIMIT 1
            """;
        String updateBudgetSql = "UPDATE budgets SET amount_limit = ?, status = 'ACTIVE', updated_at = NOW() WHERE budget_id = ?";
        String insertBudgetSql = """
            INSERT INTO budgets (user_id, category_id, amount_limit, period, start_date, end_date, status, created_at, updated_at)
            VALUES (?, ?, ?, 'MONTHLY', ?, ?, 'ACTIVE', NOW(), NOW())
            ON CONFLICT (user_id, category_id, period, start_date)
            DO UPDATE SET amount_limit = EXCLUDED.amount_limit, end_date = EXCLUDED.end_date, status = 'ACTIVE', updated_at = NOW()
            """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Long categoryId = knownCategoryId;

                // Step A: If categoryId not provided, check if category exists by name
                if (categoryId == null && !trimmedName.isEmpty()) {
                    try (PreparedStatement checkCatStmt = conn.prepareStatement(checkCatSql)) {
                        checkCatStmt.setLong(1, userId);
                        checkCatStmt.setString(2, trimmedName);
                        try (ResultSet rs = checkCatStmt.executeQuery()) {
                            if (rs.next()) {
                                categoryId = rs.getLong("id");
                            }
                        }
                    }
                }

                // Step B: If not exists -> INSERT INTO categories with RETURN_GENERATED_KEYS (NO RETURNING clause on executeUpdate)
                if (categoryId == null) {
                    PreparedStatement ps;
                    try {
                        ps = conn.prepareStatement(
                            "INSERT INTO categories (user_id, name, classification) VALUES (?, ?, 'EXPENSE')",
                            Statement.RETURN_GENERATED_KEYS
                        );
                        ps.setLong(1, userId);
                        ps.setString(2, trimmedName);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        // Fallback if classification column is absent
                        ps = conn.prepareStatement(
                            "INSERT INTO categories (user_id, name, is_system) VALUES (?, ?, false)",
                            Statement.RETURN_GENERATED_KEYS
                        );
                        ps.setLong(1, userId);
                        ps.setString(2, trimmedName);
                        ps.executeUpdate();
                    }
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            categoryId = rs.getLong(1);
                        } else {
                            throw new SQLException("Failed to retrieve category ID after insert");
                        }
                    }
                }

                // Step C & D: Check and upsert into budgets table (NO RETURNING clause on executeUpdate)
                java.time.LocalDate startDate = java.time.LocalDate.of(targetYear, targetMonth, 1);
                java.time.LocalDate endDate = startDate.plusMonths(1).minusDays(1);
                Long existingBudgetId = null;

                try (PreparedStatement checkBudgetStmt = conn.prepareStatement(checkBudgetSql)) {
                    checkBudgetStmt.setLong(1, userId);
                    checkBudgetStmt.setLong(2, categoryId);
                    checkBudgetStmt.setDate(3, Date.valueOf(startDate));
                    checkBudgetStmt.setInt(4, targetMonth);
                    checkBudgetStmt.setInt(5, targetYear);
                    try (ResultSet rs = checkBudgetStmt.executeQuery()) {
                        if (rs.next()) {
                            existingBudgetId = rs.getLong("id");
                        }
                    }
                }

                if (existingBudgetId != null) {
                    // Update existing budget limit
                    try (PreparedStatement updateBudgetStmt = conn.prepareStatement(updateBudgetSql)) {
                        updateBudgetStmt.setBigDecimal(1, monthlyCap);
                        updateBudgetStmt.setLong(2, existingBudgetId);
                        updateBudgetStmt.executeUpdate();
                    }
                } else {
                    // Insert new budget limit
                    try (PreparedStatement insertBudgetStmt = conn.prepareStatement(insertBudgetSql)) {
                        insertBudgetStmt.setLong(1, userId);
                        insertBudgetStmt.setLong(2, categoryId);
                        insertBudgetStmt.setBigDecimal(3, monthlyCap);
                        insertBudgetStmt.setDate(4, Date.valueOf(startDate));
                        insertBudgetStmt.setDate(5, Date.valueOf(endDate));
                        insertBudgetStmt.executeUpdate();
                    }
                }

                conn.commit();
                return categoryId;
            } catch (Exception e) {
                conn.rollback();
                logger.error("Database error in upsertCategoryAndBudgetLimit transaction: {}", e.getMessage(), e);
                throw new RuntimeException("Error during atomic category and budget upsert: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("SQLException in upsertCategoryAndBudgetLimit connection: {}", e.getMessage(), e);
            throw new RuntimeException("Database error during budget upsert: " + e.getMessage(), e);
        }
    }

    @Override
    public List<com.bank.model.dto.UnbudgetedCategory> getUnbudgetedCategories(Long userId, int month, int year) {
        int targetMonth = month > 0 ? month : 9;
        int targetYear = year > 0 ? year : 2026;

        String sql = """
            SELECT c.category_id AS id, c.name,
                   COALESCE(SUM(t.amount), 0) AS total_spent
            FROM categories c
            LEFT JOIN transactions t ON t.category_id = c.category_id
              AND t.account_id IN (SELECT a.account_id FROM accounts a WHERE a.user_id = ?)
              AND EXTRACT(MONTH FROM t.transaction_date) = ?
              AND EXTRACT(YEAR FROM t.transaction_date) = ?
            WHERE (c.user_id = ? OR (c.user_id IS NULL AND c.is_system = true))
              AND c.category_id NOT IN (
                  SELECT b.category_id 
                  FROM budgets b 
                  WHERE b.user_id = ? 
                    AND (b.start_date = ? OR (EXTRACT(MONTH FROM b.start_date) = ? AND EXTRACT(YEAR FROM b.start_date) = ?))
              )
            GROUP BY c.category_id, c.name
            ORDER BY c.name ASC
            """;

        List<com.bank.model.dto.UnbudgetedCategory> list = new ArrayList<>();
        java.time.LocalDate startDate = java.time.LocalDate.of(targetYear, targetMonth, 1);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setInt(2, targetMonth);
            stmt.setInt(3, targetYear);
            stmt.setLong(4, userId);
            stmt.setLong(5, userId);
            stmt.setDate(6, Date.valueOf(startDate));
            stmt.setInt(7, targetMonth);
            stmt.setInt(8, targetYear);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long id = rs.getLong("id");
                    String name = rs.getString("name");
                    java.math.BigDecimal spent = rs.getBigDecimal("total_spent");
                    list.add(new com.bank.model.dto.UnbudgetedCategory(id, name, "EXPENSE", spent != null ? spent : java.math.BigDecimal.ZERO));
                }
            }
        } catch (SQLException e) {
            logger.error("Database error in getUnbudgetedCategories for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Error fetching unbudgeted categories: " + e.getMessage(), e);
        }

        return list;
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