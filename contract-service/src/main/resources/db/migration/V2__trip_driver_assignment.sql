ALTER TABLE trips ADD COLUMN driver_id UUID;
ALTER TABLE trips ADD COLUMN assignment_pin_hash VARCHAR(64);

CREATE INDEX idx_trips_driver_id ON trips(driver_id);
