-- =====================================================
-- V6__admin_security_and_status.sql
-- Adds security question/answer for password recovery (self-service,
-- no email service required) to the admins table.
-- =====================================================

ALTER TABLE admins
    ADD COLUMN security_question VARCHAR(255),
    ADD COLUMN security_answer_hash VARCHAR(100);

-- Seed a default recovery question/answer for the initial SUPER_ADMIN
-- so account recovery works out of the box.
-- ⚠️ CHANGE THIS after first login, same as the password itself.
UPDATE admins
SET security_question = 'What is the name of this banking system?',
    security_answer_hash = '$2a$12$aqlHvQwtk8gqf1D2Q1QJUOcP7djVrzEOTPzeku.E4jii4EoI0Uvia'
WHERE username = 'superadmin';