CREATE TABLE carrier_reputations (
    carrier_id UUID PRIMARY KEY,
    score INTEGER NOT NULL DEFAULT 100 CHECK (score BETWEEN 0 AND 100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE carrier_reputation_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    carrier_id UUID NOT NULL,
    trip_id UUID NOT NULL,
    tier INTEGER NOT NULL CHECK (tier BETWEEN 1 AND 3),
    points_delta INTEGER NOT NULL CHECK (points_delta <= 0),
    score_after INTEGER NOT NULL CHECK (score_after BETWEEN 0 AND 100),
    reason VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_reputation_trip_tier UNIQUE (trip_id, tier)
);

CREATE INDEX idx_carrier_reputation_entries_carrier_created
    ON carrier_reputation_entries(carrier_id, created_at DESC);
