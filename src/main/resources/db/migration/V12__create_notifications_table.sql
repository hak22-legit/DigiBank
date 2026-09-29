-- =====================================================
-- V12__create_notifications_table.sql
-- Customer Notification & Alert System
-- =====================================================

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
