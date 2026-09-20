-- =====================================================
-- V9__add_frozen_to_user_status.sql
-- Add FROZEN status to users check constraint
-- =====================================================

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED', 'FROZEN'));
