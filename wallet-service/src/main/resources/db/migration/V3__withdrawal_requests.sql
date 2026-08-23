CREATE TABLE withdrawal_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    bank_name VARCHAR(100) NOT NULL,
    bank_account_number VARCHAR(30) NOT NULL,
    account_holder_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    processed_by UUID,
    rejection_reason VARCHAR(500),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_withdrawal_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_withdrawals_account_created
    ON withdrawal_requests(account_id, created_at DESC);

CREATE INDEX idx_withdrawals_status_created
    ON withdrawal_requests(status, created_at DESC);
