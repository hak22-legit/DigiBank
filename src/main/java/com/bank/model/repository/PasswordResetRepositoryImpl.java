package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.entity.PasswordResetToken;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

public class PasswordResetRepositoryImpl implements PasswordResetRepository {

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        String sql = """
            INSERT INTO password_reset_tokens (user_id, otp_code, attempts, expires_at, is_used, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING token_id
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime createdAt = token.getCreatedAt() != null ? token.getCreatedAt() : now;

            stmt.setLong(1, token.getUserId());
            stmt.setString(2, token.getOtpCode());
            stmt.setInt(3, token.getAttempts() != null ? token.getAttempts() : 0);
            stmt.setTimestamp(4, Timestamp.valueOf(token.getExpiresAt()));
            stmt.setBoolean(5, token.getIsUsed() != null ? token.getIsUsed() : false);
            stmt.setTimestamp(6, Timestamp.valueOf(createdAt));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    token.setTokenId(rs.getLong("token_id"));
                    token.setCreatedAt(createdAt);
                }
            }
            return token;
        } catch (SQLException e) {
            throw new RuntimeException("Error saving password reset token", e);
        }
    }

    @Override
    public Optional<PasswordResetToken> findLatestActiveToken(Long userId) {
        String sql = """
            SELECT * FROM password_reset_tokens
            WHERE user_id = ? AND is_used = FALSE AND expires_at > CURRENT_TIMESTAMP AND attempts < 3
            ORDER BY created_at DESC
            LIMIT 1
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding latest active reset token for user: " + userId, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<PasswordResetToken> findByUserIdAndOtp(Long userId, String otpCode) {
        String sql = """
            SELECT * FROM password_reset_tokens
            WHERE user_id = ? AND otp_code = ? AND is_used = FALSE AND expires_at > CURRENT_TIMESTAMP
            ORDER BY created_at DESC
            LIMIT 1
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setString(2, otpCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding reset token for user " + userId + " and OTP " + otpCode, e);
        }
        return Optional.empty();
    }

    @Override
    public void updateAttempts(Long tokenId, int attempts) {
        String sql = "UPDATE password_reset_tokens SET attempts = ? WHERE token_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, attempts);
            stmt.setLong(2, tokenId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating token attempts for token: " + tokenId, e);
        }
    }

    @Override
    public void markAsUsed(Long tokenId) {
        String sql = "UPDATE password_reset_tokens SET is_used = TRUE WHERE token_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, tokenId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error marking token as used for token: " + tokenId, e);
        }
    }

    @Override
    public void invalidateAllForUser(Long userId) {
        String sql = "UPDATE password_reset_tokens SET is_used = TRUE WHERE user_id = ? AND is_used = FALSE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error invalidating reset tokens for user: " + userId, e);
        }
    }

    private PasswordResetToken mapRow(ResultSet rs) throws SQLException {
        Timestamp expiresTs = rs.getTimestamp("expires_at");
        Timestamp createdTs = rs.getTimestamp("created_at");

        return PasswordResetToken.builder()
                .tokenId(rs.getLong("token_id"))
                .userId(rs.getLong("user_id"))
                .otpCode(rs.getString("otp_code"))
                .attempts(rs.getInt("attempts"))
                .expiresAt(expiresTs != null ? expiresTs.toLocalDateTime() : null)
                .isUsed(rs.getBoolean("is_used"))
                .createdAt(createdTs != null ? createdTs.toLocalDateTime() : null)
                .build();
    }
}
