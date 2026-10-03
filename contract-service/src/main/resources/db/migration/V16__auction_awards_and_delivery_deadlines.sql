ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS auction_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS winning_bid_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS expected_delivery_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deposit_hold_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS deposit_amount NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS deposit_released_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_trips_auction_id
    ON trips(auction_id)
    WHERE auction_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trips_expected_delivery_at
    ON trips(expected_delivery_at)
    WHERE expected_delivery_at IS NOT NULL;
