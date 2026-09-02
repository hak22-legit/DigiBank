-- =====================================================
-- V5__seed_super_admin.sql
-- Seeds the first SUPER_ADMIN so the system can bootstrap RBAC.
-- ⚠️ Default password: ChangeMe123!  — CHANGE IMMEDIATELY after first login.
-- =====================================================

INSERT INTO admins (username, email, password_hash, full_name, role, status, created_at, updated_at)
VALUES (
           'superadmin',
           'superadmin@digibank.local',
           '$2a$12$85D9VpUlX/kGWjEfszWdeuECU8307jeMxS2mifHq/hExamkbtDeUm',
           'System Administrator',
           'SUPER_ADMIN',
           'ACTIVE',
           CURRENT_TIMESTAMP,
           CURRENT_TIMESTAMP
       );