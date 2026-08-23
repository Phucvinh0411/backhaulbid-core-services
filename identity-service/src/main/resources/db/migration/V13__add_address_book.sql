CREATE TABLE address_book_entries (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id     UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    label          VARCHAR(120) NOT NULL,
    contact_name   VARCHAR(150) NOT NULL,
    contact_phone  VARCHAR(30) NOT NULL,
    province       VARCHAR(120) NOT NULL,
    detail         VARCHAR(500) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_address_book_entries_account_updated
    ON address_book_entries (account_id, updated_at DESC);
