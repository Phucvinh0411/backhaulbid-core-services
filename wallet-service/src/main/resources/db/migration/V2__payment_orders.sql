CREATE TABLE payment_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    provider VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_transaction_id VARCHAR(100),
    checkout_url VARCHAR(1000),
    expires_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_order_status CHECK (status IN ('CREATED', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_payment_orders_account_created
    ON payment_orders(account_id, created_at DESC);
