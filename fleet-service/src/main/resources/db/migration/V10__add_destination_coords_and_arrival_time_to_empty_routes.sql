-- V8 is used for a different migration in databases that already reached V9.
ALTER TABLE empty_routes
    ADD COLUMN IF NOT EXISTS dest_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS dest_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS expected_arrival_time TIMESTAMP;

UPDATE empty_routes
SET dest_latitude = 21.0285,
    dest_longitude = 105.8542,
    expected_arrival_time = expected_empty_time + INTERVAL '1 day 12 hours'
WHERE id = '55555555-e001-0000-0000-000000000001'
  AND expected_arrival_time IS NULL;
