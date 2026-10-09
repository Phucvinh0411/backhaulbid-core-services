-- V3.2 claim preparation: a shipper's claim draft with private evidence references. No provider integration
-- exists, so nothing here can reach SUBMITTED, UNDER_REVIEW, APPROVED, DENIED or CLOSED from this application;
-- those states are allowed by the CHECK only so that a future provider link can record a confirmed answer.
-- Additive only.

CREATE TABLE insurance_claims (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    shipper_id UUID NOT NULL,
    carrier_id UUID NOT NULL,
    incident_type VARCHAR(20) NOT NULL CHECK (incident_type IN ('DAMAGE', 'LOSS', 'THEFT')),
    occurred_at TIMESTAMPTZ NOT NULL,
    description VARCHAR(1000) NOT NULL CHECK (length(btrim(description)) >= 10),
    claimed_amount NUMERIC(14,2) CHECK (claimed_amount IS NULL OR claimed_amount > 0),
    status VARCHAR(24) NOT NULL CHECK (status IN (
        'DRAFT', 'NEEDS_EVIDENCE', 'READY_FOR_PROVIDER', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'DENIED', 'CLOSED')),
    provider_reference VARCHAR(80),               -- set only from a provider's confirmed response
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_insurance_claims_trip ON insurance_claims(trip_id, created_at DESC);

CREATE TABLE insurance_claim_evidence (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES insurance_claims(id) ON DELETE CASCADE,
    kind VARCHAR(30) NOT NULL CHECK (kind IN (
        'INVOICE', 'RECEIPT', 'CONTRACT', 'PAYMENT_PROOF', 'PHOTO', 'VIDEO', 'PACKAGING',
        'CARRIER_CONFIRMATION', 'POLICE_REPORT', 'OTHER')),
    file_url VARCHAR(500) NOT NULL,               -- private media object URL; never returned to clients
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_insurance_claim_evidence_claim ON insurance_claim_evidence(claim_id, uploaded_at);
