-- =====================================================
-- V11__add_budget_limits_and_category_upsert.sql
-- Supports atomic UPSERT operations on categories and budget limits
-- =====================================================

-- 1. Ensure categories has classification and updated_at
ALTER TABLE categories ADD COLUMN IF NOT EXISTS classification VARCHAR(20) DEFAULT 'EXPENSE';
ALTER TABLE categories ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 2. Ensure unique index on (user_id, name) for PostgreSQL ON CONFLICT (user_id, name)
CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_user_id_name ON categories (user_id, name);

-- 3. Create budget_limits table
CREATE TABLE IF NOT EXISTS budget_limits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    category_id BIGINT NOT NULL REFERENCES categories(category_id),
    monthly_cap NUMERIC(19,4) NOT NULL CHECK (monthly_cap > 0),
    month INT NOT NULL,
    year INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, category_id, month, year)
);

CREATE INDEX IF NOT EXISTS idx_budget_limits_user_period ON budget_limits (user_id, month, year);
