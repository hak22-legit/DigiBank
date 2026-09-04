-- =====================================================
-- V1__create_schema.sql
-- Enterprise Banking System - Initial Schema
-- =====================================================

-- 1. USERS (customers)
CREATE TABLE users (
                       user_id         BIGSERIAL PRIMARY KEY,
                       username        VARCHAR(50)  NOT NULL UNIQUE,
                       email           VARCHAR(100) NOT NULL UNIQUE,
                       password_hash   VARCHAR(100) NOT NULL,
                       full_name       VARCHAR(100) NOT NULL,
                       phone           VARCHAR(20),
                       status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                           CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED')),
                       created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. ADMINS
CREATE TABLE admins (
                        admin_id        BIGSERIAL PRIMARY KEY,
                        username        VARCHAR(50)  NOT NULL UNIQUE,
                        email           VARCHAR(100) NOT NULL UNIQUE,
                        password_hash   VARCHAR(100) NOT NULL,
                        full_name       VARCHAR(100) NOT NULL,
                        role            VARCHAR(30)  NOT NULL
                            CHECK (role IN ('SUPER_ADMIN', 'LOAN_OFFICER', 'COMPLIANCE_OFFICER')),
                        status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'INACTIVE')),
                        created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. ACCOUNTS
CREATE TABLE accounts (
                          account_id      BIGSERIAL PRIMARY KEY,
                          user_id         BIGINT       NOT NULL REFERENCES users(user_id),
                          account_number  VARCHAR(20)  NOT NULL UNIQUE,
                          account_type    VARCHAR(20)  NOT NULL
                              CHECK (account_type IN ('SAVINGS', 'CHECKING', 'LOAN')),
                          balance         NUMERIC(19,4) NOT NULL DEFAULT 0
                              CHECK (balance >= 0),
                          currency        VARCHAR(3)   NOT NULL DEFAULT 'USD',
                          status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
                          created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_status ON accounts(status);

-- 4. CATEGORIES (expense categories)
CREATE TABLE categories (
                            category_id     BIGSERIAL PRIMARY KEY,
                            name            VARCHAR(50)  NOT NULL UNIQUE,
                            description     VARCHAR(200),
                            is_system       BOOLEAN      NOT NULL DEFAULT FALSE,
                            created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. TRANSACTIONS
CREATE TABLE transactions (
                              transaction_id      BIGSERIAL PRIMARY KEY,
                              account_id          BIGINT       NOT NULL REFERENCES accounts(account_id),
                              related_account_id  BIGINT       REFERENCES accounts(account_id),
                              category_id         BIGINT       REFERENCES categories(category_id),
                              transaction_type    VARCHAR(20)  NOT NULL
                                  CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'PAYMENT', 'LOAN_DISBURSEMENT', 'LOAN_REPAYMENT')),
                              amount              NUMERIC(19,4) NOT NULL
                                  CHECK (amount > 0),
                              currency            VARCHAR(3)   NOT NULL DEFAULT 'USD',
                              description         VARCHAR(255),
                              status              VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED'
                                  CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')),
                              idempotency_key     UUID         NOT NULL UNIQUE,
                              transaction_date    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);
CREATE INDEX idx_transactions_idempotency ON transactions(idempotency_key);

-- 6. LOANS
CREATE TABLE loans (
                       loan_id             BIGSERIAL PRIMARY KEY,
                       user_id             BIGINT       NOT NULL REFERENCES users(user_id),
                       account_id          BIGINT       REFERENCES accounts(account_id),
                       requested_amount    NUMERIC(19,4) NOT NULL CHECK (requested_amount > 0),
                       approved_amount     NUMERIC(19,4) CHECK (approved_amount > 0),
                       interest_rate       NUMERIC(5,2)  NOT NULL DEFAULT 0,
                       term_months         INTEGER       NOT NULL CHECK (term_months > 0),
                       monthly_income      NUMERIC(19,4),
                       monthly_expense     NUMERIC(19,4),
                       existing_debt       NUMERIC(19,4) DEFAULT 0,
                       credit_score        INTEGER       CHECK (credit_score BETWEEN 300 AND 850),
                       risk_score          NUMERIC(5,2),
                       risk_level          VARCHAR(10)   CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
                       status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'ACTIVE', 'PAID_OFF', 'DEFAULTED')),
                       approved_by         BIGINT        REFERENCES admins(admin_id),
                       approved_at         TIMESTAMP,
                       rejection_reason    VARCHAR(255),
                       outstanding_balance NUMERIC(19,4) DEFAULT 0,
                       created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_loans_user_id ON loans(user_id);
CREATE INDEX idx_loans_status ON loans(status);

-- 7. LOAN_PAYMENTS
CREATE TABLE loan_payments (
                               payment_id          BIGSERIAL PRIMARY KEY,
                               loan_id             BIGINT       NOT NULL REFERENCES loans(loan_id),
                               account_id          BIGINT       NOT NULL REFERENCES accounts(account_id),
                               amount              NUMERIC(19,4) NOT NULL CHECK (amount > 0),
                               principal_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
                               interest_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
                               payment_date        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               transaction_id      BIGINT       REFERENCES transactions(transaction_id),
                               created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_loan_payments_loan_id ON loan_payments(loan_id);

-- 8. SAVING_GOALS
CREATE TABLE saving_goals (
                              goal_id             BIGSERIAL PRIMARY KEY,
                              user_id             BIGINT       NOT NULL REFERENCES users(user_id),
                              name                VARCHAR(100) NOT NULL,
                              target_amount       NUMERIC(19,4) NOT NULL CHECK (target_amount > 0),
                              current_amount      NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (current_amount >= 0),
                              deadline            DATE,
                              status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                                  CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
                              created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_saving_goals_user_id ON saving_goals(user_id);

-- 9. BUDGETS
CREATE TABLE budgets (
                         budget_id           BIGSERIAL PRIMARY KEY,
                         user_id             BIGINT       NOT NULL REFERENCES users(user_id),
                         category_id         BIGINT       NOT NULL REFERENCES categories(category_id),
                         amount_limit        NUMERIC(19,4) NOT NULL CHECK (amount_limit > 0),
                         period              VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY'
                             CHECK (period IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
                         start_date          DATE         NOT NULL,
                         end_date            DATE,
                         status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'INACTIVE')),
                         created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         UNIQUE (user_id, category_id, period, start_date)
);

CREATE INDEX idx_budgets_user_id ON budgets(user_id);

-- 10. AUDIT_LOGS
CREATE TABLE audit_logs (
                            audit_id            BIGSERIAL PRIMARY KEY,
                            admin_id            BIGINT       REFERENCES admins(admin_id),
                            action              VARCHAR(50)  NOT NULL,
                            target_table        VARCHAR(50),
                            target_id           BIGINT,
                            details             TEXT,
                            ip_address          VARCHAR(45),
                            created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_admin_id ON audit_logs(admin_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- 11. FRAUD_ALERTS
CREATE TABLE fraud_alerts (
                              alert_id            BIGSERIAL PRIMARY KEY,
                              user_id             BIGINT       REFERENCES users(user_id),
                              account_id          BIGINT       REFERENCES accounts(account_id),
                              transaction_id      BIGINT       REFERENCES transactions(transaction_id),
                              risk_level          VARCHAR(10)  NOT NULL
                                  CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
                              status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
                                  CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'CONFIRMED_FRAUD')),
                              description         TEXT         NOT NULL,
                              investigated_by     BIGINT       REFERENCES admins(admin_id),
                              resolved_at         TIMESTAMP,
                              resolution_notes    TEXT,
                              created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fraud_alerts_status ON fraud_alerts(status);
CREATE INDEX idx_fraud_alerts_user_id ON fraud_alerts(user_id);

-- =====================================================
-- Seed data: default categories
-- =====================================================
INSERT INTO categories (name, description, is_system) VALUES
                                                          ('Food', 'Food and dining', TRUE),
                                                          ('Transportation', 'Transport and travel', TRUE),
                                                          ('Shopping', 'Shopping and retail', TRUE),
                                                          ('Entertainment', 'Entertainment and leisure', TRUE),
                                                          ('Education', 'Education and learning', TRUE),
                                                          ('Bills', 'Utilities and bills', TRUE),
                                                          ('Healthcare', 'Medical and health', TRUE),
                                                          ('Other', 'Other expenses', TRUE);