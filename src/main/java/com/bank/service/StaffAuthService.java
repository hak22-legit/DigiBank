package com.bank.service;

import com.bank.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Service for staff authentication tracking, failed login counter management,
 * and automatic lockout threshold enforcement.
 */
public class StaffAuthService {
    private static final Logger logger = LoggerFactory.getLogger(StaffAuthService.class);

    /**
     * Records a failed password attempt for an internal staff account.
     * Atomically increments failed_login_attempts and transitions status to SUSPENDED
     * if attempts reach or exceed 5.
     * Ensures conn.commit() is called immediately so the update persists even if the session aborts.
     */
    public static void recordFailedStaffLogin(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        String normalized = username.trim().toLowerCase();

        String updateStaffSql = """
            UPDATE staff 
            SET failed_login_attempts = failed_login_attempts + 1,
                status = CASE 
                           WHEN failed_login_attempts + 1 >= 5 THEN 'SUSPENDED' 
                           ELSE status 
                         END,
                updated_at = NOW()
            WHERE LOWER(username) = LOWER(?)
        """;

        String updateAdminsSql = """
            UPDATE admins 
            SET failed_login_attempts = failed_login_attempts + 1,
                status = CASE 
                           WHEN failed_login_attempts + 1 >= 5 THEN 'SUSPENDED' 
                           ELSE status 
                         END,
                updated_at = NOW()
            WHERE LOWER(username) = LOWER(?)
        """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement staffStmt = conn.prepareStatement(updateStaffSql)) {
                    staffStmt.setString(1, normalized);
                    staffStmt.executeUpdate();
                } catch (SQLException ignored) {
                    // staff table may be optional if admins table is primary
                }

                try (PreparedStatement adminStmt = conn.prepareStatement(updateAdminsSql)) {
                    adminStmt.setString(1, normalized);
                    adminStmt.executeUpdate();
                }

                conn.commit();
                logger.info("Recorded failed login attempt for staff user: {}", normalized);
            } catch (SQLException ex) {
                conn.rollback();
                logger.error("Failed to commit staff failed login update for: {}", normalized, ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Throwable e) {
            logger.warn("Database connection error recording staff failed login: {}", e.getMessage());
        }
    }

    /**
     * Resets failed login counter and updates last_login_at upon successful staff authentication.
     * Commits transaction immediately.
     */
    public static void recordSuccessfulStaffLogin(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        String normalized = username.trim().toLowerCase();

        String updateStaffSql = """
            UPDATE staff 
            SET failed_login_attempts = 0, last_login_at = NOW() 
            WHERE LOWER(username) = LOWER(?)
        """;

        String updateAdminsSql = """
            UPDATE admins 
            SET failed_login_attempts = 0, last_login_at = NOW() 
            WHERE LOWER(username) = LOWER(?)
        """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement staffStmt = conn.prepareStatement(updateStaffSql)) {
                    staffStmt.setString(1, normalized);
                    staffStmt.executeUpdate();
                } catch (SQLException ignored) {}

                try (PreparedStatement adminStmt = conn.prepareStatement(updateAdminsSql)) {
                    adminStmt.setString(1, normalized);
                    adminStmt.executeUpdate();
                }

                conn.commit();
                logger.info("Reset failed login counter on successful authentication for staff: {}", normalized);
            } catch (SQLException ex) {
                conn.rollback();
                logger.error("Failed to reset failed login counter for staff: {}", normalized, ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Throwable e) {
            logger.warn("Database connection error resetting staff login counter: {}", e.getMessage());
        }
    }

    /**
     * Explicitly unlocks staff account and resets failed login attempts to 0.
     * Used by Super Admin password resets and unsuspension.
     */
    public static void resetStaffLockout(Long staffId, String username) {
        String updateStaffSql = """
            UPDATE staff 
            SET failed_login_attempts = 0, status = 'ACTIVE', updated_at = NOW() 
            WHERE id = ? OR LOWER(username) = LOWER(?)
        """;

        String updateAdminsSql = """
            UPDATE admins 
            SET failed_login_attempts = 0, status = 'ACTIVE', updated_at = NOW() 
            WHERE admin_id = ? OR LOWER(username) = LOWER(?)
        """;

        String uname = username != null ? username.trim().toLowerCase() : "";
        Long id = staffId != null ? staffId : -1L;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement staffStmt = conn.prepareStatement(updateStaffSql)) {
                    staffStmt.setLong(1, id);
                    staffStmt.setString(2, uname);
                    staffStmt.executeUpdate();
                } catch (SQLException ignored) {}

                try (PreparedStatement adminStmt = conn.prepareStatement(updateAdminsSql)) {
                    adminStmt.setLong(1, id);
                    adminStmt.setString(2, uname);
                    adminStmt.executeUpdate();
                }

                conn.commit();
                logger.info("Reset staff lockout for id={}, username={}", id, uname);
            } catch (SQLException ex) {
                conn.rollback();
                logger.error("Failed to commit staff lockout reset for: {}", uname, ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Throwable e) {
            logger.warn("Database connection error resetting staff lockout: {}", e.getMessage());
        }
    }
}
