-- V3.3 sharing with the carrier. READY_FOR_PROVIDER becomes READY_TO_SHARE: the checklist is complete and the claim
-- may be shared with the trip's carrier. Nothing is sent to any provider, so the old name is retired. Adds the share
-- and acknowledgement records, and a revision number so that evidence added after a share does not change what the
-- carrier was given. Additive apart from the status rename.

ALTER TABLE insurance_claims DROP CONSTRAINT insurance_claims_status_check;

UPDATE insurance_claims SET status = 'READY_TO_SHARE' WHERE status = 'READY_FOR_PROVIDER';

ALTER TABLE insurance_claims ADD CONSTRAINT insurance_claims_status_check CHECK (status IN (
    'DRAFT', 'NEEDS_EVIDENCE', 'READY_TO_SHARE', 'SHARED_WITH_CARRIER', 'CARRIER_ACKNOWLEDGED',
    'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'DENIED', 'CLOSED'));

ALTER TABLE insurance_claims
    ADD COLUMN revision INTEGER NOT NULL DEFAULT 1 CHECK (revision >= 1),
    ADD COLUMN shared_revision INTEGER CHECK (shared_revision IS NULL OR shared_revision >= 1),
    ADD COLUMN shared_at TIMESTAMPTZ,
    ADD COLUMN shared_by UUID,
    ADD COLUMN carrier_acknowledged_revision INTEGER CHECK (carrier_acknowledged_revision IS NULL OR carrier_acknowledged_revision >= 1),
    ADD COLUMN carrier_acknowledged_at TIMESTAMPTZ,
    ADD COLUMN carrier_acknowledged_by UUID,
    ADD CONSTRAINT ck_insurance_claims_share_set CHECK (
        (shared_revision IS NULL) = (shared_at IS NULL) AND (shared_at IS NULL) = (shared_by IS NULL)),
    ADD CONSTRAINT ck_insurance_claims_ack_set CHECK (
        (carrier_acknowledged_revision IS NULL) = (carrier_acknowledged_at IS NULL)
        AND (carrier_acknowledged_at IS NULL) = (carrier_acknowledged_by IS NULL));

-- Evidence is append-only. The revision it was added in lets the carrier's view stop at the shared revision.
ALTER TABLE insurance_claim_evidence
    ADD COLUMN revision INTEGER NOT NULL DEFAULT 1 CHECK (revision >= 1);
