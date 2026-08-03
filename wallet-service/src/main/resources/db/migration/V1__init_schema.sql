CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    frozen_balance NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (frozen_balance >= 0 AND frozen_balance <= balance),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(30),
    reference_code VARCHAR(100),
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_transaction_type CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'FREEZE', 'UNFREEZE', 'TRANSFER', 'REFUND', 'PENALTY', 'AUCTION_FEE')),
    CONSTRAINT chk_transaction_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_payment_method CHECK (payment_method IS NULL OR payment_method IN ('BANK_TRANSFER', 'MOMO', 'ZALOPAY'))
);

CREATE INDEX idx_transactions_wallet_created ON transactions(wallet_id, created_at DESC);
CREATE INDEX idx_transactions_reference_code ON transactions(reference_code);
