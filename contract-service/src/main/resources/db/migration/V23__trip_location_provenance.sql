-- Preserve check-ins as business events and prevent revoked-driver manual points becoming live positions.
ALTER TABLE trip_location_updates ADD COLUMN actor_role VARCHAR(20);
ALTER TABLE trip_location_updates ADD COLUMN milestone_id UUID REFERENCES trip_milestones(id);
ALTER TABLE trip_location_updates DROP CONSTRAINT chk_trip_location_source;
ALTER TABLE trip_location_updates ADD CONSTRAINT chk_trip_location_source CHECK(source IN ('MANUAL','GPS','CHECK_IN'));
-- Existing manual rows keep their original author. Unknown actor roles are never guessed.
