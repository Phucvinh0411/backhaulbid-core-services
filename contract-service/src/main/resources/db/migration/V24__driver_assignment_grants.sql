-- V3.1 driver access without an account: one grant per assignment, redeemed into one driver session.
-- Additive only; existing trips, tracking sessions and GPS rows keep their meaning.

-- assignment_version increases on every assign/reissue so old grants and sessions stop matching.
ALTER TABLE trips ADD COLUMN assignment_version BIGINT NOT NULL DEFAULT 0;
-- The driver session currently allowed on the trip. Kept apart from driver_account_id (legacy accounts).
ALTER TABLE trips ADD COLUMN driver_session_id UUID;
CREATE UNIQUE INDEX uq_trips_driver_session ON trips(driver_session_id) WHERE driver_session_id IS NOT NULL;

CREATE TABLE trip_driver_grants (
    id UUID PRIMARY KEY,                         -- UUIDv7; an identifier, never a credential on its own
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    driver_profile_id UUID NOT NULL,             -- fleet profile managed by the carrier
    carrier_id UUID NOT NULL,
    assignment_version BIGINT NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,             -- SHA-256 of the 32-byte random secret; plaintext is never stored
    state VARCHAR(12) NOT NULL CHECK (state IN ('ISSUED', 'REDEEMED', 'REVOKED')),
    expires_at TIMESTAMPTZ NOT NULL,             -- redeem deadline (24 h)
    issued_by UUID NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    redeem_request_id UUID,
    driver_session_id UUID UNIQUE,
    redeemed_at TIMESTAMPTZ,
    session_revoked_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(40),
    CONSTRAINT trip_driver_grants_redeemed_has_session CHECK (
        state <> 'REDEEMED' OR (driver_session_id IS NOT NULL AND redeem_request_id IS NOT NULL AND redeemed_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trip_driver_grants_version ON trip_driver_grants(trip_id, assignment_version);
CREATE INDEX idx_trip_driver_grants_trip ON trip_driver_grants(trip_id, issued_at DESC);

-- GPS from a driver session is attributed to the session, not to a pretend account.
ALTER TABLE trip_tracking_sessions ALTER COLUMN driver_account_id DROP NOT NULL;
ALTER TABLE trip_tracking_sessions ADD COLUMN driver_session_id UUID;
ALTER TABLE trip_tracking_sessions ADD CONSTRAINT trip_tracking_sessions_one_actor
    CHECK ((driver_account_id IS NULL) <> (driver_session_id IS NULL));
CREATE UNIQUE INDEX uq_active_tracking_driver_session ON trip_tracking_sessions(driver_session_id)
    WHERE status = 'ACTIVE' AND driver_session_id IS NOT NULL;

ALTER TABLE trip_location_updates ADD COLUMN actor_type VARCHAR(20) NOT NULL DEFAULT 'ACCOUNT'
    CHECK (actor_type IN ('ACCOUNT', 'DRIVER_SESSION'));

-- Rollback (after reverting application code, losing driver-session GPS attribution):
-- ALTER TABLE trip_location_updates DROP COLUMN actor_type;
-- DROP INDEX uq_active_tracking_driver_session;
-- ALTER TABLE trip_tracking_sessions DROP CONSTRAINT trip_tracking_sessions_one_actor;
-- DELETE FROM trip_tracking_sessions WHERE driver_session_id IS NOT NULL;
-- ALTER TABLE trip_tracking_sessions DROP COLUMN driver_session_id;
-- ALTER TABLE trip_tracking_sessions ALTER COLUMN driver_account_id SET NOT NULL;
-- DROP TABLE trip_driver_grants;
-- DROP INDEX uq_trips_driver_session;
-- ALTER TABLE trips DROP COLUMN driver_session_id;
-- ALTER TABLE trips DROP COLUMN assignment_version;
