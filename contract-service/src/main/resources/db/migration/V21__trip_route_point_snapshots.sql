ALTER TABLE trips
    ADD COLUMN pickup_latitude DOUBLE PRECISION,
    ADD COLUMN pickup_longitude DOUBLE PRECISION,
    ADD COLUMN pickup_label VARCHAR(120),
    ADD COLUMN pickup_address VARCHAR(1000),
    ADD COLUMN pickup_coordinate_source VARCHAR(30),
    ADD COLUMN delivery_latitude DOUBLE PRECISION,
    ADD COLUMN delivery_longitude DOUBLE PRECISION,
    ADD COLUMN delivery_label VARCHAR(120),
    ADD COLUMN delivery_address VARCHAR(1000),
    ADD COLUMN delivery_coordinate_source VARCHAR(30),
    ADD COLUMN route_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN award_route_snapshot_hash VARCHAR(64);

ALTER TABLE trips ADD CONSTRAINT trip_pickup_coordinate_pair CHECK (
    (pickup_latitude IS NULL AND pickup_longitude IS NULL AND pickup_coordinate_source IS NULL)
    OR (pickup_latitude IS NOT NULL AND pickup_longitude IS NOT NULL
        AND pickup_latitude BETWEEN -90 AND 90 AND pickup_longitude BETWEEN -180 AND 180
        AND pickup_coordinate_source IS NOT NULL AND pickup_coordinate_source = 'USER_CONFIRMED')
);
ALTER TABLE trips ADD CONSTRAINT trip_delivery_coordinate_pair CHECK (
    (delivery_latitude IS NULL AND delivery_longitude IS NULL AND delivery_coordinate_source IS NULL)
    OR (delivery_latitude IS NOT NULL AND delivery_longitude IS NOT NULL
        AND delivery_latitude BETWEEN -90 AND 90 AND delivery_longitude BETWEEN -180 AND 180
        AND delivery_coordinate_source IS NOT NULL AND delivery_coordinate_source = 'USER_CONFIRMED')
);
ALTER TABLE trips ADD CONSTRAINT trip_route_version_nonnegative CHECK (route_version >= 0);

CREATE TABLE trip_route_point_audits (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id),
    actor_id UUID NOT NULL,
    route_version BIGINT NOT NULL,
    previous_pickup_latitude DOUBLE PRECISION,
    previous_pickup_longitude DOUBLE PRECISION,
    previous_delivery_latitude DOUBLE PRECISION,
    previous_delivery_longitude DOUBLE PRECISION,
    pickup_latitude DOUBLE PRECISION NOT NULL,
    pickup_longitude DOUBLE PRECISION NOT NULL,
    delivery_latitude DOUBLE PRECISION NOT NULL,
    delivery_longitude DOUBLE PRECISION NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (trip_id, route_version)
);
CREATE INDEX trip_route_point_audits_trip_time ON trip_route_point_audits (trip_id, confirmed_at DESC);

-- Rollback (manual): DROP TABLE trip_route_point_audits;
-- ALTER TABLE trips DROP CONSTRAINT trip_pickup_coordinate_pair, DROP CONSTRAINT trip_delivery_coordinate_pair,
-- DROP CONSTRAINT trip_route_version_nonnegative, DROP COLUMN pickup_latitude, DROP COLUMN pickup_longitude,
-- DROP COLUMN pickup_label, DROP COLUMN pickup_address, DROP COLUMN pickup_coordinate_source,
-- DROP COLUMN delivery_latitude, DROP COLUMN delivery_longitude, DROP COLUMN delivery_label,
-- DROP COLUMN delivery_address, DROP COLUMN delivery_coordinate_source,
-- DROP COLUMN route_version, DROP COLUMN award_route_snapshot_hash;
