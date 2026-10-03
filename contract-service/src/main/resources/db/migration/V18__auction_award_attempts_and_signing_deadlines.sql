ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS award_attempt_id VARCHAR(100);

DROP INDEX IF EXISTS uq_trips_auction_id;

CREATE INDEX IF NOT EXISTS idx_trips_auction_id
    ON trips(auction_id)
    WHERE auction_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_trips_award_attempt_id
    ON trips(award_attempt_id)
    WHERE award_attempt_id IS NOT NULL;

ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS signing_deadline_at TIMESTAMPTZ;

ALTER TABLE contracts
    DROP CONSTRAINT IF EXISTS chk_contract_status;

ALTER TABLE contracts
    ADD CONSTRAINT chk_contract_status
        CHECK (status IN ('DRAFT', 'WAITING_SIGNATURE', 'SIGNED', 'CANCELLED', 'EXPIRED'));
