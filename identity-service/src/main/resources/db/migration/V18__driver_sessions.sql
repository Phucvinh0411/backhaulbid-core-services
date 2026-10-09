-- V3.1 driver sessions: a driver signs in with a one-time assignment code, never with an Account.
-- Identity issues the JWT; contract owns the assignment and decides whether the session is current.
CREATE TABLE driver_sessions (
    id UUID PRIMARY KEY,                         -- session ID chosen once by contract when the code was redeemed
    grant_id UUID NOT NULL UNIQUE,
    trip_id UUID NOT NULL,
    driver_profile_id UUID NOT NULL,
    assignment_version BIGINT NOT NULL,
    redeem_request_id UUID NOT NULL,
    refresh_hash VARCHAR(64) NOT NULL,            -- SHA-256 of the current refresh secret
    previous_refresh_hash VARCHAR(64),            -- accepted only inside the short rotation grace window
    rotated_at TIMESTAMPTZ,
    replay_cipher BYTEA,                         -- AES-GCM token pair for a lost-response retry, short TTL
    replay_expires_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,             -- at most 7 days after redeem
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_driver_sessions_refresh ON driver_sessions(refresh_hash);
CREATE INDEX idx_driver_sessions_expires ON driver_sessions(expires_at);

-- Shared, persistent redeem rate limit (per IP, and per IP + grant).
CREATE TABLE driver_redeem_throttle (
    bucket VARCHAR(100) PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL
);

-- Rollback: DROP TABLE driver_redeem_throttle; DROP TABLE driver_sessions;
