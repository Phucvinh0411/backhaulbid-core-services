CREATE TABLE trip_delay_settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    tier INTEGER NOT NULL CHECK (tier BETWEEN 1 AND 3),
    late_minutes BIGINT NOT NULL CHECK (late_minutes > 0),
    cumulative_penalty_percent INTEGER NOT NULL CHECK (cumulative_penalty_percent IN (5, 50, 100)),
    total_compensation_amount NUMERIC(19, 2) NOT NULL CHECK (total_compensation_amount >= 0),
    incremental_compensation_amount NUMERIC(19, 2) NOT NULL CHECK (incremental_compensation_amount >= 0),
    points_deducted INTEGER NOT NULL CHECK (points_deducted >= 0),
    wallet_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reputation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    overall_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_error VARCHAR(1000),
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_trip_delay_settlement_trip_tier UNIQUE (trip_id, tier),
    CONSTRAINT chk_trip_delay_settlement_status CHECK (
        wallet_status IN ('PENDING', 'COMPLETED', 'FAILED') AND
        reputation_status IN ('PENDING', 'COMPLETED', 'FAILED') AND
        notification_status IN ('PENDING', 'COMPLETED', 'FAILED') AND
        overall_status IN ('PENDING', 'COMPLETED', 'FAILED')
    )
);

CREATE INDEX idx_trip_delay_settlements_status_updated
    ON trip_delay_settlements(overall_status, updated_at);
