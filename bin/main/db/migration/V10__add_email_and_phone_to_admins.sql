-- =====================================================
-- V10__add_email_and_phone_to_admins.sql
-- Adds email (if not existing) and phone_number to admins table.
-- =====================================================

ALTER TABLE admins 
ADD COLUMN IF NOT EXISTS email VARCHAR(120),
ADD COLUMN IF NOT EXISTS phone_number VARCHAR(30);
