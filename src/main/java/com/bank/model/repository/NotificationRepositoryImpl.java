package com.bank.model.repository;

import com.bank.database.DatabaseConnection;
import com.bank.model.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NotificationRepositoryImpl implements NotificationRepository {
    private static final Logger logger = LoggerFactory.getLogger(NotificationRepositoryImpl.class);

    public NotificationRepositoryImpl() {
        initTable();
    }

    private void initTable() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS notifications (
                notification_id BIGSERIAL PRIMARY KEY,
                user_id         BIGINT NOT NULL REFERENCES users(user_id),
                title           VARCHAR(100) NOT NULL,
                message         TEXT NOT NULL,
                type            VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
                is_read         BOOLEAN NOT NULL DEFAULT FALSE,
                created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
            CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON notifications(is_read);
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException e) {
            logger.warn("Notification table check/init warning: {}", e.getMessage());
        }
    }

    @Override
    public void saveWithConnection(Connection conn, Long userId, String title, String message, String type) throws SQLException {
        String sql = """
            INSERT INTO notifications (user_id, title, message, type, is_read, created_at)
            VALUES (?, ?, ?, ?, false, NOW())
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setString(2, title);
            stmt.setString(3, message);
            stmt.setString(4, type != null ? type : "GENERAL");
            stmt.executeUpdate();
        }
    }

    @Override
    public Notification save(Notification notification) {
        String sql = """
            INSERT INTO notifications (user_id, title, message, type, is_read, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING notification_id
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            stmt.setLong(1, notification.getUserId());
            stmt.setString(2, notification.getTitle());
            stmt.setString(3, notification.getMessage());
            stmt.setString(4, notification.getType() != null ? notification.getType() : "GENERAL");
            stmt.setBoolean(5, notification.isRead());
            stmt.setTimestamp(6, Timestamp.valueOf(notification.getCreatedAt() != null ? notification.getCreatedAt() : now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    notification.setNotificationId(rs.getLong(1));
                    if (notification.getCreatedAt() == null) {
                        notification.setCreatedAt(now);
                    }
                }
            }
            return notification;
        } catch (SQLException e) {
            throw new RuntimeException("Error saving notification", e);
        }
    }

    @Override
    public List<Notification> findUnreadByUserId(Long userId) {
        String sql = "SELECT * FROM notifications WHERE user_id = ? AND is_read = false ORDER BY created_at DESC";
        List<Notification> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.warn("Error finding unread notifications for user {}", userId, e);
        }
        return list;
    }

    @Override
    public Optional<Notification> findLatestUnreadByUserId(Long userId) {
        String sql = "SELECT * FROM notifications WHERE user_id = ? AND is_read = false ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.warn("Error finding latest unread notification for user {}", userId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<Notification> findByUserId(Long userId) {
        String sql = "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC";
        List<Notification> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.warn("Error finding notifications for user {}", userId, e);
        }
        return list;
    }

    @Override
    public void markAsRead(Long notificationId) {
        String sql = "UPDATE notifications SET is_read = true WHERE notification_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, notificationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Error marking notification {} as read", notificationId, e);
        }
    }

    @Override
    public void markAllAsReadForUser(Long userId) {
        String sql = "UPDATE notifications SET is_read = true WHERE user_id = ? AND is_read = false";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Error marking notifications as read for user {}", userId, e);
        }
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        return Notification.builder()
                .notificationId(rs.getLong("notification_id"))
                .userId(rs.getLong("user_id"))
                .title(rs.getString("title"))
                .message(rs.getString("message"))
                .type(rs.getString("type"))
                .isRead(rs.getBoolean("is_read"))
                .createdAt(ts != null ? ts.toLocalDateTime() : null)
                .build();
    }
}
