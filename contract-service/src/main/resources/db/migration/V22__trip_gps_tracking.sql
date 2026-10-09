-- Additive GPS ingestion; V21 is reserved for route snapshots. No existing migration is altered.
CREATE TABLE trip_tracking_sessions (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    driver_account_id UUID NOT NULL,
    status VARCHAR(12) NOT NULL CHECK(status IN ('ACTIVE', 'STOPPED')),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    last_received_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_active_tracking_trip ON trip_tracking_sessions(trip_id) WHERE status='ACTIVE';
CREATE UNIQUE INDEX uq_active_tracking_driver ON trip_tracking_sessions(driver_account_id) WHERE status='ACTIVE';
CREATE INDEX idx_tracking_session_trip_started ON trip_tracking_sessions(trip_id, started_at DESC);

ALTER TABLE trip_location_updates ADD COLUMN tracking_session_id UUID REFERENCES trip_tracking_sessions(id);
ALTER TABLE trip_location_updates ADD COLUMN sample_id UUID;
ALTER TABLE trip_location_updates ADD COLUMN captured_at TIMESTAMPTZ;
ALTER TABLE trip_location_updates ADD COLUMN accuracy_meters DOUBLE PRECISION;
CREATE UNIQUE INDEX uq_gps_session_sample ON trip_location_updates(tracking_session_id, sample_id)
    WHERE tracking_session_id IS NOT NULL AND sample_id IS NOT NULL;
CREATE INDEX idx_trip_location_captured ON trip_location_updates(trip_id, captured_at DESC);
ALTER TABLE trip_location_updates ADD COLUMN ingestion_sequence BIGSERIAL NOT NULL;
CREATE UNIQUE INDEX uq_trip_location_ingestion ON trip_location_updates(ingestion_sequence);
CREATE INDEX idx_trip_location_cursor ON trip_location_updates(trip_id, ingestion_sequence);
-- Rollback: disable GPS uploads before removing these indexes/columns/table; retained coordinates would be lost.
