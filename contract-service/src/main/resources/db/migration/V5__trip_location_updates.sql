CREATE TABLE IF NOT EXISTS trip_location_updates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    label VARCHAR(160),
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(30),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_trip_location_source CHECK (source IN ('MANUAL', 'GPS')),
    CONSTRAINT chk_trip_location_status CHECK (status IS NULL OR status IN ('WAITING_PICKUP', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_trip_location_updates_trip_time
    ON trip_location_updates(trip_id, recorded_at ASC);
