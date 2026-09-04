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
        String sql = "SELECT * FROM admins WHERE email = ?";
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
        String sql = "SELECT * FROM admins WHERE username = ?";
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

    @Override
    public Admin save(Admin admin) {
        return admin.getAdminId() == null ? insert(admin) : update(admin);
    }

    private Admin insert(Admin admin) {
        String sql = """
        INSERT INTO admins (username, email, password_hash, full_name, role, status,
                             security_question, security_answer_hash, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING admin_id
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setString(1, admin.getUsername());
            stmt.setString(2, admin.getEmail());
            stmt.setString(3, admin.getPasswordHash());
            stmt.setString(4, admin.getFullName());
            stmt.setString(5, admin.getRole().name());
            stmt.setString(6, admin.getStatus().name());
            stmt.setString(7, admin.getSecurityQuestion());
            stmt.setString(8, admin.getSecurityAnswerHash());
            stmt.setTimestamp(9, Timestamp.valueOf(now));
            stmt.setTimestamp(10, Timestamp.valueOf(now));

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
        SET username = ?, email = ?, password_hash = ?, full_name = ?,
            role = ?, status = ?, security_question = ?, security_answer_hash = ?, updated_at = ?
        WHERE admin_id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            stmt.setString(1, admin.getUsername());
            stmt.setString(2, admin.getEmail());
            stmt.setString(3, admin.getPasswordHash());
            stmt.setString(4, admin.getFullName());
            stmt.setString(5, admin.getRole().name());
            stmt.setString(6, admin.getStatus().name());
            stmt.setString(7, admin.getSecurityQuestion());
            stmt.setString(8, admin.getSecurityAnswerHash());
            stmt.setTimestamp(9, Timestamp.valueOf(now));
            stmt.setLong(10, admin.getAdminId());

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
        return Admin.builder()
                .adminId(rs.getLong("admin_id"))
                .username(rs.getString("username"))
                .email(rs.getString("email"))
                .passwordHash(rs.getString("password_hash"))
                .fullName(rs.getString("full_name"))
                .role(AdminRole.valueOf(rs.getString("role")))
                .status(AdminStatus.valueOf(rs.getString("status")))
                .securityQuestion(rs.getString("security_question"))
                .securityAnswerHash(rs.getString("security_answer_hash"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }



}