package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.entity.AuditLog;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AuditLogRepositoryImpl implements AuditLogRepository {

    private static final String BASE_SELECT = """
        SELECT 
            a.audit_id,
            a.admin_id,
            COALESCE(adm.username, CAST(a.admin_id AS text)) AS actor_name,
            a.action,
            a.target_table,
            a.target_id,
            a.details,
            a.ip_address,
            a.created_at
        FROM audit_logs a
        LEFT JOIN admins adm ON a.admin_id = adm.admin_id
        """;

    @Override
    public Optional<AuditLog> findById(Long logId) {
        String sql = BASE_SELECT + " WHERE a.audit_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, logId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding audit log by id: " + logId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<AuditLog> findByAdminId(Long adminId) {
        String sql = BASE_SELECT + " WHERE a.admin_id = ? ORDER BY a.created_at DESC";
        List<AuditLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, adminId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) logs.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding audit logs for admin: " + adminId, e);
        }
        return logs;
    }

    @Override
    public List<AuditLog> findAll() {
        String sql = BASE_SELECT + " ORDER BY a.created_at DESC";
        List<AuditLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) logs.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all audit logs", e);
        }
        return logs;
    }

    @Override
    public AuditLog save(AuditLog log) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return saveWithConnection(conn, log);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving audit log", e);
        }
    }

    @Override
    public AuditLog saveWithConnection(Connection conn, AuditLog log) throws SQLException {
        String sql = """
            INSERT INTO audit_logs (admin_id, action, target_table, target_id, details, ip_address, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING audit_id
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            if (log.getAdminId() != null) {
                stmt.setLong(1, log.getAdminId());
            } else {
                stmt.setNull(1, Types.BIGINT);
            }
            stmt.setString(2, log.getAction());
            stmt.setString(3, log.getTargetTable());
            if (log.getTargetId() != null) {
                stmt.setLong(4, log.getTargetId());
            } else {
                stmt.setNull(4, Types.BIGINT);
            }
            stmt.setString(5, log.getDetails());
            stmt.setString(6, log.getIpAddress());
            stmt.setTimestamp(7, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    log.setLogId(rs.getLong("audit_id"));
                    log.setCreatedAt(now);
                }
            }
            return log;
        }
    }

    @Override
    public List<AuditLog> findPaginated(int offset, int limit) {
        String sql = BASE_SELECT + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?";
        List<AuditLog> logs = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) logs.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding paginated audit logs", e);
        }
        return logs;
    }

    @Override
    public long countAll() {
        String sql = "SELECT COUNT(*) FROM audit_logs";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error counting audit logs", e);
        }
        return 0;
    }

    private AuditLog mapRow(ResultSet rs) throws SQLException {
        Long adminId = rs.getObject("admin_id") != null ? rs.getLong("admin_id") : null;
        Long targetId = rs.getObject("target_id") != null ? rs.getLong("target_id") : null;
        String actorName = null;
        try {
            actorName = rs.getString("actor_name");
        } catch (SQLException ignored) {}

        return AuditLog.builder()
                .logId(rs.getLong("audit_id"))
                .adminId(adminId)
                .actorName(actorName)
                .action(rs.getString("action"))
                .targetTable(rs.getString("target_table"))
                .targetId(targetId)
                .details(rs.getString("details"))
                .ipAddress(rs.getString("ip_address"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }
}