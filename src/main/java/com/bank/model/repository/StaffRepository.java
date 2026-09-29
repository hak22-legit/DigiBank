package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Repository for Staff entity persistence, temporary password resets, and atomic audit logging.
 */
public class StaffRepository {
    private static final Logger logger = LoggerFactory.getLogger(StaffRepository.class);

    static {
        ensureSchemaAligned();
    }

    private static void ensureSchemaAligned() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE admins ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN DEFAULT FALSE");
            stmt.execute("ALTER TABLE admins ADD COLUMN IF NOT EXISTS failed_login_attempts INT DEFAULT 0");
            stmt.execute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor VARCHAR(50)");
            stmt.execute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS target VARCHAR(50)");
            stmt.execute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS risk VARCHAR(20) DEFAULT 'INFO'");
            stmt.execute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS result VARCHAR(20) DEFAULT 'SUCCESS'");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS staff (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(50),
                    email VARCHAR(100),
                    password_hash VARCHAR(100),
                    full_name VARCHAR(100),
                    role VARCHAR(30),
                    status VARCHAR(20) DEFAULT 'ACTIVE',
                    must_change_password BOOLEAN DEFAULT TRUE,
                    failed_login_attempts INT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
        } catch (Exception e) {
            logger.debug("Schema alignment notice in StaffRepository: {}", e.getMessage());
        }
    }

    /**
     * Executes atomic temporary password override for staff member, hashing password with BCrypt
     * and writing an audit trail record in a single transaction.
     */
    public boolean resetStaffPassword(Long staffId, String plainTextPassword, String actorUsername, Long actorAdminId) {
        if (staffId == null || plainTextPassword == null || plainTextPassword.isBlank()) {
            return false;
        }

        String passwordHash = BCrypt.hashpw(plainTextPassword, BCrypt.gensalt(12));

        String updateStaffSql = """
            UPDATE staff
            SET password_hash = ?,
                must_change_password = TRUE,
                failed_login_attempts = 0,
                status = 'ACTIVE',
                updated_at = NOW()
            WHERE id = ?
        """;

        String updateAdminSql = """
            UPDATE admins
            SET password_hash = ?,
                must_change_password = TRUE,
                failed_login_attempts = 0,
                status = 'ACTIVE',
                updated_at = NOW()
            WHERE admin_id = ?
        """;

        String auditSql = """
            INSERT INTO audit_logs (admin_id, actor, action, target_table, target_id, target, risk, result, details, created_at)
            VALUES (?, ?, 'RESET_STAFF_PASSWORD', 'staff', ?, ?, 'MED', 'SUCCESS', 'Temporary password provisioned with mandatory first-login reset flag.', NOW())
        """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Update staff table if row exists
                try (PreparedStatement staffStmt = conn.prepareStatement(updateStaffSql)) {
                    staffStmt.setString(1, passwordHash);
                    staffStmt.setLong(2, staffId);
                    staffStmt.executeUpdate();
                } catch (Exception ignored) {
                    // If staff table does not have matching row, proceed
                }

                // 2. Update admins table
                try (PreparedStatement adminStmt = conn.prepareStatement(updateAdminSql)) {
                    adminStmt.setString(1, passwordHash);
                    adminStmt.setLong(2, staffId);
                    adminStmt.executeUpdate();
                }

                // 3. Write audit log record
                String targetFormatted = "#ADM-" + String.format("%02d", staffId);
                try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                    if (actorAdminId != null) {
                        auditStmt.setLong(1, actorAdminId);
                    } else {
                        auditStmt.setNull(1, java.sql.Types.BIGINT);
                    }
                    auditStmt.setString(2, actorUsername != null ? actorUsername : "superadmin");
                    auditStmt.setLong(3, staffId);
                    auditStmt.setString(4, targetFormatted);
                    auditStmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                logger.error("Failed atomic password reset for staff id: {}", staffId, ex);
                throw new RuntimeException("Database error updating staff credential", ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Database connection error during staff password reset", e);
            throw new RuntimeException("Database connection failure", e);
        }
    }
}
