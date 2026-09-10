

ALTER TABLE loan_payments
    ADD COLUMN due_date       DATE,
    ADD COLUMN status         VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
        CHECK (status IN ('SCHEDULED', 'COMPLETED', 'LATE', 'MISSED')),
    ADD COLUMN payment_method VARCHAR(30);

COMMENT ON COLUMN loan_payments.due_date IS 'Scheduled due date for this installment';
COMMENT ON COLUMN loan_payments.status IS 'SCHEDULED, COMPLETED, LATE, MISSED';
COMMENT ON COLUMN loan_payments.payment_method IS 'e.g. ACCOUNT_TRANSFER, CASH, EXTERNAL';