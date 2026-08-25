package com.bank.repository;

import com.bank.database.DatabaseConnection;
import com.bank.enums.GoalStatus;
import com.bank.model.SavingGoal;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SavingGoalRepositoryImpl implements SavingGoalRepository {

    @Override
    public Optional<SavingGoal> findById(Long goalId) {
        String sql = "SELECT * FROM saving_goals WHERE goal_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, goalId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding saving goal by id: " + goalId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<SavingGoal> findByUserId(Long userId) {
        String sql = "SELECT * FROM saving_goals WHERE user_id = ? ORDER BY goal_id";
        List<SavingGoal> goals = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) goals.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding saving goals for user: " + userId, e);
        }
        return goals;
    }

    @Override
    public List<SavingGoal> findAll() {
        String sql = "SELECT * FROM saving_goals ORDER BY goal_id";
        List<SavingGoal> goals = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) goals.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all saving goals", e);
        }
        return goals;
    }

    @Override
    public SavingGoal save(SavingGoal goal) {
        return goal.getGoalId() == null ? insert(goal) : update(goal);
    }

    private SavingGoal insert(SavingGoal goal) {
        String sql = """
            INSERT INTO saving_goals (user_id, name, target_amount, current_amount, deadline, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING goal_id
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setLong(1, goal.getUserId());
            stmt.setString(2, goal.getName());
            stmt.setBigDecimal(3, goal.getTargetAmount());
            stmt.setBigDecimal(4, goal.getCurrentAmount());
            stmt.setDate(5, goal.getDeadline() != null ? Date.valueOf(goal.getDeadline()) : null);
            stmt.setString(6, goal.getStatus().name());
            stmt.setTimestamp(7, Timestamp.valueOf(now));
            stmt.setTimestamp(8, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    goal.setGoalId(rs.getLong("goal_id"));
                    goal.setCreatedAt(now);
                    goal.setUpdatedAt(now);
                }
            }
            return goal;
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting saving goal", e);
        }
    }

    private SavingGoal update(SavingGoal goal) {
        String sql = """
            UPDATE saving_goals
            SET name = ?, target_amount = ?, current_amount = ?, deadline = ?, status = ?, updated_at = ?
            WHERE goal_id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setString(1, goal.getName());
            stmt.setBigDecimal(2, goal.getTargetAmount());
            stmt.setBigDecimal(3, goal.getCurrentAmount());
            stmt.setDate(4, goal.getDeadline() != null ? Date.valueOf(goal.getDeadline()) : null);
            stmt.setString(5, goal.getStatus().name());
            stmt.setTimestamp(6, Timestamp.valueOf(now));
            stmt.setLong(7, goal.getGoalId());

            stmt.executeUpdate();
            goal.setUpdatedAt(now);
            return goal;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating saving goal", e);
        }
    }

    @Override
    public boolean deleteById(Long goalId) {
        String sql = "DELETE FROM saving_goals WHERE goal_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, goalId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting saving goal: " + goalId, e);
        }
    }

    private SavingGoal mapRow(ResultSet rs) throws SQLException {
        Date deadline = rs.getDate("deadline");
        return SavingGoal.builder()
                .goalId(rs.getLong("goal_id"))
                .userId(rs.getLong("user_id"))
                .name(rs.getString("name"))
                .targetAmount(rs.getBigDecimal("target_amount"))
                .currentAmount(rs.getBigDecimal("current_amount"))
                .deadline(deadline != null ? deadline.toLocalDate() : null)
                .status(GoalStatus.valueOf(rs.getString("status")))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}