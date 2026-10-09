-- driver_id remains the fleet profile ID; account access is granted only by a successful claim.
ALTER TABLE trips ADD COLUMN driver_account_id UUID;
ALTER TABLE trips ADD COLUMN assignment_pin_expires_at TIMESTAMPTZ;
ALTER TABLE trips ADD COLUMN assignment_pin_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE trips ADD CONSTRAINT trips_assignment_pin_attempts_check
    CHECK (assignment_pin_attempts BETWEEN 0 AND 5);
CREATE INDEX idx_trips_driver_account_created ON trips (driver_account_id, created_at DESC);

-- Existing assignments have no verified account link and must be reissued by their carrier.
-- Manual rollback (after reverting application code):
-- DROP INDEX idx_trips_driver_account_created;
-- ALTER TABLE trips DROP CONSTRAINT trips_assignment_pin_attempts_check;
-- ALTER TABLE trips DROP COLUMN driver_account_id;
-- ALTER TABLE trips DROP COLUMN assignment_pin_expires_at;
-- ALTER TABLE trips DROP COLUMN assignment_pin_attempts;
