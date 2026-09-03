-- =====================================================
-- V4__add_user_id_to_categories.sql
-- Categories can now be private to a user (custom categories)
-- or shared system categories (user_id IS NULL)
-- =====================================================

ALTER TABLE categories
    ADD COLUMN user_id BIGINT REFERENCES users(user_id);

COMMENT ON COLUMN categories.user_id IS 'NULL = system category (shared by all users). Set = custom category owned by that user.';

-- The old global UNIQUE constraint on name no longer makes sense,
-- since two different users should be able to name their categories the same thing.
-- Replace it with a constraint that only prevents duplicates within the same owner
-- (system categories still can't duplicate each other, and a user can't duplicate
-- their own category name).
ALTER TABLE categories DROP CONSTRAINT categories_name_key;

CREATE UNIQUE INDEX idx_categories_name_per_owner
    ON categories (name, COALESCE(user_id, 0));