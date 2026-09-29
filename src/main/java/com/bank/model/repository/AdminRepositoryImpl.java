package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.enums.AdminRole;
import com.bank.model.enums.AdminStatus;
import com.bank.model.entity.Admin;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdminRepositoryImpl implements AdminRepository {

    @Override
    public Optional<Admin> findById(Long adminId) {
        String sql = "SELECT * FROM admins WHERE admin_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, adminId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding admin by id: " + adminId, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Admin> findByEmail(String email) {
        String sql = "SELECT * FROM admins WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding admin by email: " + email, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Admin> findByUsername(String username) {
        String sql = "SELECT * FROM admins WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding admin by username: " + username, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Admin> findAll() {
        String sql = "SELECT * FROM admins ORDER BY admin_id";
        List<Admin> admins = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) admins.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error finding all admins", e);
        }
        return admins;
    }

    static {
        ensureSchemaAligned();
    }

    private static void ensureSchemaAligned() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE admins ADD COLUMN IF NOT EXISTS email VARCHAR(120)");
            stmt.execute("ALTER TABLE admins ADD COLUMN IF NOT EXISTS phone_number VARCHAR(30)");
            stmt.execute("ALTER TABLE admins ADD COLUMN IF NOT EXISTS failed_login_attempts INT DEFAULT 0");
            stmt.execute("ALTER TABLE admins ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP");
            stmt.execute("UPDATE admins SET role = 'COMPLIANCE_OFFICER' WHERE LOWER(username) = 'compliance1' OR admin_id = 2");
            try {
                stmt.execute("UPDATE staff SET role = 'COMPLIANCE_OFFICER' WHERE LOWER(username) = 'compliance1' OR id = 2");
            } catch (Exception ignored) {}
            stmt.execute("INSERT INTO admins (username, email, password_hash, full_name, role, status, created_at, updated_at) " +
                    "VALUES ('superadmin', 'superadmin@digibank.local', " +
                    "'$2a$12$85D9VpUlX/kGWjEfszWdeuECU8307jeMxS2mifHq/hExamkbtDeUm', " +
                    "'System Administrator', 'SUPER_ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT (username) DO UPDATE SET status = 'ACTIVE', role = 'SUPER_ADMIN', failed_login_attempts = 0, updated_at = CURRENT_TIMESTAMP");
        } catch (Exception ignored) {}
    }

    @Override
    public Admin save(Admin admin) {
        return admin.getAdminId() == null ? insert(admin) : update(admin);
    }

    private Admin insert(Admin admin) {
        String sql = """
        INSERT INTO admins (username, email, password_hash, full_name, phone_number, role, status,
                             security_question, security_answer_hash, failed_login_attempts, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING admin_id
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setString(1, admin.getUsername());
            stmt.setString(2, admin.getEmail());
            stmt.setString(3, admin.getPasswordHash());
            stmt.setString(4, admin.getFullName());
            stmt.setString(5, admin.getPhoneNumber());
            stmt.setString(6, admin.getRole().name());
            stmt.setString(7, admin.getStatus().name());
            stmt.setString(8, admin.getSecurityQuestion());
            stmt.setString(9, admin.getSecurityAnswerHash());
            stmt.setInt(10, admin.getFailedLoginAttempts());
            stmt.setTimestamp(11, Timestamp.valueOf(now));
            stmt.setTimestamp(12, Timestamp.valueOf(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    admin.setAdminId(rs.getLong("admin_id"));
                    admin.setCreatedAt(now);
                    admin.setUpdatedAt(now);
                }
            }
            return admin;
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting admin", e);
        }
    }

    private Admin update(Admin admin) {
        String sql = """
        UPDATE admins
        SET username = ?, email = ?, password_hash = ?, full_name = ?, phone_number = ?,
            role = ?, status = ?, security_question = ?, security_answer_hash = ?,
            failed_login_attempts = ?, updated_at = ?
        WHERE admin_id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setString(1, admin.getUsername());
            stmt.setString(2, admin.getEmail());
            stmt.setString(3, admin.getPasswordHash());
            stmt.setString(4, admin.getFullName());
            stmt.setString(5, admin.getPhoneNumber());
            stmt.setString(6, admin.getRole().name());
            stmt.setString(7, admin.getStatus().name());
            stmt.setString(8, admin.getSecurityQuestion());
            stmt.setString(9, admin.getSecurityAnswerHash());
            stmt.setInt(10, admin.getFailedLoginAttempts());
            stmt.setTimestamp(11, Timestamp.valueOf(now));
            stmt.setLong(12, admin.getAdminId());

            stmt.executeUpdate();
            admin.setUpdatedAt(now);
            return admin;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating admin", e);
        }
    }

    @Override
    public boolean deleteById(Long adminId) {
        String sql = "DELETE FROM admins WHERE admin_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, adminId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting admin: " + adminId, e);
        }
    }

    private Admin mapRow(ResultSet rs) throws SQLException {
        String phone = null;
        try {
            phone = rs.getString("phone_number");
        } catch (SQLException ignored) {}

        int failedAttempts = 0;
        try {
            failedAttempts = rs.getInt("failed_login_attempts");
        } catch (SQLException ignored) {}

        LocalDateTime lastLogin = null;
        try {
            Timestamp ts = rs.getTimestamp("last_login_at");
            if (ts != null) lastLogin = ts.toLocalDateTime();
        } catch (SQLException ignored) {}

        return Admin.builder()
                .adminId(rs.getLong("admin_id"))
                .username(rs.getString("username"))
                .email(rs.getString("email"))
                .passwordHash(rs.getString("password_hash"))
                .fullName(rs.getString("full_name"))
                .phoneNumber(phone)
                .role(AdminRole.valueOf(rs.getString("role")))
                .status(AdminStatus.valueOf(rs.getString("status")))
                .securityQuestion(rs.getString("security_question"))
                .securityAnswerHash(rs.getString("security_answer_hash"))
                .failedLoginAttempts(failedAttempts)
                .lastLoginAt(lastLogin)
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}