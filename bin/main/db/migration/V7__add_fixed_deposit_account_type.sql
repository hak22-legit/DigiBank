-- =====================================================
-- V7__add_fixed_deposit_account_type.sql
-- Allow FIXED_DEPOSIT account type in accounts table
-- =====================================================

ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_account_type_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_account_type_check CHECK (account_type IN ('SAVINGS', 'CHECKING', 'LOAN', 'FIXED_DEPOSIT'));
