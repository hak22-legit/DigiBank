package com.bank.model.repository;

import com.bank.model.entity.Notification;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    void saveWithConnection(Connection conn, Long userId, String title, String message, String type) throws SQLException;
    Notification save(Notification notification);
    List<Notification> findUnreadByUserId(Long userId);
    Optional<Notification> findLatestUnreadByUserId(Long userId);
    List<Notification> findByUserId(Long userId);
    void markAsRead(Long notificationId);
    void markAllAsReadForUser(Long userId);
}
