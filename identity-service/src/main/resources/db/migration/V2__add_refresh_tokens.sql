-- ============================================
-- V2__add_refresh_tokens.sql
-- Add refresh_tokens table for JWT token management
-- ============================================

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(512) NOT NULL UNIQUE,
    account_id  UUID        NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    expiry_date TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens (token);
CREATE INDEX idx_refresh_tokens_account_id ON refresh_tokens (account_id);

COMMENT ON TABLE refresh_tokens IS 'Stores JWT refresh tokens for session management';
