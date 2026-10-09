-- No raw LDS/MRZ/APDU/key retention. Bounded photos and summary are AES-GCM encrypted.
CREATE TABLE ekyc_review_reports (
    id UUID PRIMARY KEY REFERENCES ekyc_capture_sessions(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','VERIFIED','REJECTED','SUPERSEDED')),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    evidence_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE, reviewed_by UUID REFERENCES accounts(id),
    review_reason VARCHAR(500), verification_method VARCHAR(40) NOT NULL,
    capture_conclusion VARCHAR(24) NOT NULL, sdk_checks TEXT NOT NULL,
    document_compared BOOLEAN NOT NULL DEFAULT FALSE, face_compared BOOLEAN NOT NULL DEFAULT FALSE,
    summary_encrypted BYTEA NOT NULL, front_encrypted BYTEA, back_encrypted BYTEA, selfie_encrypted BYTEA,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_ekyc_review_owner ON ekyc_review_reports(account_id, submitted_at DESC);
CREATE INDEX idx_ekyc_review_queue ON ekyc_review_reports(status, submitted_at);
CREATE UNIQUE INDEX idx_ekyc_review_one_pending ON ekyc_review_reports(account_id) WHERE status='PENDING';
ALTER TABLE ekyc_verifications ADD COLUMN verification_method VARCHAR(40);
ALTER TABLE ekyc_verifications ALTER COLUMN full_name TYPE VARCHAR(1024);
CREATE TABLE ekyc_review_audit (
    id UUID PRIMARY KEY, report_id UUID REFERENCES ekyc_review_reports(id),
    actor_id UUID NOT NULL REFERENCES accounts(id), action VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_ekyc_review_audit_report ON ekyc_review_audit(report_id, occurred_at);
-- Manual rollback: stop capture/review traffic; DROP TABLE ekyc_review_audit; DROP TABLE ekyc_review_reports;
-- ALTER TABLE ekyc_verifications DROP COLUMN verification_method;
-- Retain widened full_name: narrowing encrypted values would truncate existing data.
