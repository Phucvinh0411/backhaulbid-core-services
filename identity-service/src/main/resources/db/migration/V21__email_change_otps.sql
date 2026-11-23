CREATE TABLE IF NOT EXISTS email_change_otps (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    pending_email VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0
);
