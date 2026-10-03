-- Create trip milestones for check-in along a transport trip.
-- V7 is used for a different migration in databases that already reached V14.
CREATE TABLE IF NOT EXISTS trip_milestones (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id          UUID         NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    milestone_name   VARCHAR(255) NOT NULL,
    target_lat       DOUBLE PRECISION NOT NULL,
    target_lng       DOUBLE PRECISION NOT NULL,
    actual_lat       DOUBLE PRECISION,
    actual_lng       DOUBLE PRECISION,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reached_at       TIMESTAMPTZ,
    sequence_order   INTEGER      NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_milestone_status CHECK (status IN ('PENDING', 'REACHED')),
    CONSTRAINT chk_milestone_target_lat  CHECK (target_lat BETWEEN -90  AND 90),
    CONSTRAINT chk_milestone_target_lng  CHECK (target_lng BETWEEN -180 AND 180),
    CONSTRAINT chk_milestone_actual_lat  CHECK (actual_lat IS NULL OR actual_lat BETWEEN -90  AND 90),
    CONSTRAINT chk_milestone_actual_lng  CHECK (actual_lng IS NULL OR actual_lng BETWEEN -180 AND 180),
    CONSTRAINT chk_milestone_sequence    CHECK (sequence_order > 0)
);

CREATE INDEX IF NOT EXISTS idx_trip_milestones_trip_id
    ON trip_milestones(trip_id);

CREATE INDEX IF NOT EXISTS idx_trip_milestones_trip_seq
    ON trip_milestones(trip_id, sequence_order ASC);

COMMENT ON TABLE trip_milestones IS 'Trip check-in milestones';
