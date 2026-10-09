-- V3.2 pickup handover: one record per trip, written before pickup by the driver (or carrier) and
-- confirmed by the shipper. A trip cannot leave WAITING_PICKUP without a photo and that confirmation.
-- Additive only; existing trips keep their status and history.

CREATE TABLE trip_handovers (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL UNIQUE REFERENCES trips(id) ON DELETE CASCADE,
    cargo_category VARCHAR(80) NOT NULL,
    package_count INTEGER CHECK (package_count IS NULL OR package_count > 0),
    gross_weight_kg NUMERIC(12,2) NOT NULL CHECK (gross_weight_kg > 0),
    condition_status VARCHAR(20) NOT NULL CHECK (condition_status IN ('GOOD', 'DAMAGED')),
    condition_note VARCHAR(500),
    seal_number VARCHAR(60),
    place_note VARCHAR(255),
    recorded_by UUID NOT NULL,                    -- driver session ID or carrier account ID
    recorded_by_type VARCHAR(10) NOT NULL CHECK (recorded_by_type IN ('DRIVER', 'CARRIER')),
    recorded_at TIMESTAMPTZ NOT NULL,
    shipper_signer_name VARCHAR(120),             -- name typed by the shipper who handed the goods over
    shipper_confirmed_by UUID,
    shipper_confirmed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_trip_handover_damage_note CHECK (condition_status <> 'DAMAGED' OR length(btrim(condition_note)) > 0),
    CONSTRAINT ck_trip_handover_confirm_pair CHECK ((shipper_confirmed_at IS NULL) = (shipper_confirmed_by IS NULL))
);

CREATE TABLE trip_handover_photos (
    handover_id UUID NOT NULL REFERENCES trip_handovers(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position >= 0),
    url VARCHAR(500) NOT NULL,                    -- media object URL in the private handover-photos folder
    PRIMARY KEY (handover_id, position)
);
