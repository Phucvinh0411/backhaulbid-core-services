-- Opaque pairing credentials only: no card data, images, MRZ or biometric scores.
CREATE TABLE ekyc_capture_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    role VARCHAR(50) NOT NULL CHECK (role IN ('SHIPPER', 'CARRIER')),
    state VARCHAR(20) NOT NULL CHECK (state IN ('CREATED','PAIRED','APPROVED','CAPTURING','SUBMITTED','CANCELLED','EXPIRED')),
    pairing_token_hash VARCHAR(64), device_nonce_hash VARCHAR(64), upload_grant_hash VARCHAR(64),
    confirmation_code VARCHAR(6), expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, attempt_id UUID, receipt_id UUID,
    evidence_digest VARCHAR(64), version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_ekyc_capture_owner_created ON ekyc_capture_sessions(account_id, created_at DESC);
CREATE UNIQUE INDEX idx_ekyc_capture_one_active ON ekyc_capture_sessions(account_id)
    WHERE state IN ('CREATED','PAIRED','APPROVED','CAPTURING');
-- Manual rollback (requires stopping NFC capture traffic first): DROP TABLE ekyc_capture_sessions;
