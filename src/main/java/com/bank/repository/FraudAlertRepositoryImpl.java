package com.bank.repository;

import com.bank.database.DatabaseConnection;
import com.bank.enums.FraudStatus;
import com.bank.enums.RiskLevel;
import com.bank.model.FraudAlert;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FraudAlertRepositoryImpl implements FraudAlertRepository {

    @Override
    public Optional<FraudAlert> findById(Long alertId) {
        String sql = "SELECT * FROM fraud_alerts WHERE alert_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, alertId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding fraud alert by id: " + alertId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<FraudAlert> findByUserId(Long userId) {
        String sql = "SELECT * FROM fraud_alerts WHERE user_id = ? ORDER BY created_at DESC";
        List<FraudAlert> alerts = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) alerts.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding fraud alerts for user: " + userId, e);
        }
        return alerts;
    }

    @Override
    public List<FraudAlert> findByStatus(String status) {
        String sql = "SELECT * FROM fraud_alerts WHERE status = ? ORDER BY created_at";
        List<FraudAlert> alerts = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) alerts.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding fraud alerts by status: " + status, e);
        }
        return alerts;
    }

    @Override
    public List<FraudAlert> findAll() {
        String sql = "SELECT * FROM fraud_alerts ORDER BY created_at DESC";
        List<FraudAlert> alerts = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) alerts.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all fraud alerts", e);
        }
        return alerts;
    }

    @Override
    public FraudAlert save(FraudAlert alert) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return saveWithConnection(conn, alert);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving fraud alert", e);
        }
    }

    @Override
    public FraudAlert saveWithConnection(Connection conn, FraudAlert alert) throws SQLException {
        String sql = """
            INSERT INTO fraud_alerts (user_id, account_id, transaction_id, risk_level, status,
                                       description, investigated_by, resolved_at, resolution_notes,
                                       created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING alert_id
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            setNullableLong(stmt, 1, alert.getUserId());
            setNullableLong(stmt, 2, alert.getAccountId());
            setNullableLong(stmt, 3, alert.getTransactionId());
            stmt.setString(4, alert.getRiskLevel().name());
            stmt.setString(5, alert.getStatus().name());
            stmt.setString(6, alert.getDescription());
            setNullableLong(stmt, 7, alert.getInvestigatedBy());
            stmt.setTimestamp(8, alert.getResolvedAt() != null ? Timestamp.valueOf(alert.getResolvedAt()) : null);
            stmt.setString(9, alert.getResolutionNotes());
            stmt.setTimestamp(10, Timestamp.valueOf(now));
            stmt.setTimestamp(11, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    alert.setAlertId(rs.getLong("alert_id"));
                    alert.setCreatedAt(now);
                    alert.setUpdatedAt(now);
                }
            }
            return alert;
        }
    }

    private void setNullableLong(PreparedStatement stmt, int index, Long value) throws SQLException {
        if (value != null) stmt.setLong(index, value);
        else stmt.setNull(index, Types.BIGINT);
    }

    private FraudAlert mapRow(ResultSet rs) throws SQLException {
        Long userId = rs.getObject("user_id") != null ? rs.getLong("user_id") : null;
        Long accountId = rs.getObject("account_id") != null ? rs.getLong("account_id") : null;
        Long transactionId = rs.getObject("transaction_id") != null ? rs.getLong("transaction_id") : null;
        Long investigatedBy = rs.getObject("investigated_by") != null ? rs.getLong("investigated_by") : null;
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");

        return FraudAlert.builder()
                .alertId(rs.getLong("alert_id"))
                .userId(userId)
                .accountId(accountId)
                .transactionId(transactionId)
                .riskLevel(RiskLevel.valueOf(rs.getString("risk_level")))
                .status(FraudStatus.valueOf(rs.getString("status")))
                .description(rs.getString("description"))
                .investigatedBy(investigatedBy)
                .resolvedAt(resolvedAt != null ? resolvedAt.toLocalDateTime() : null)
                .resolutionNotes(rs.getString("resolution_notes"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}