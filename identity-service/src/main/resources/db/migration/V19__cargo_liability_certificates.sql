-- V19 cargo liability certificates for carriers. Additive only: no existing row is changed and no legacy record
-- becomes VERIFIED here. A certificate satisfies a shipper's requirement only after an admin sets verified_by and
-- verified_at (see CargoLiabilityRules).
ALTER TABLE insurance_infos
    ADD COLUMN coverage_type VARCHAR(40),
    ADD COLUMN certificate_key VARCHAR(500),
    ADD COLUMN submitted_at TIMESTAMPTZ,
    ADD COLUMN verified_by UUID,
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD COLUMN rejection_reason VARCHAR(500);

ALTER TABLE insurance_infos
    ADD CONSTRAINT chk_insurance_coverage_type CHECK (coverage_type IS NULL OR coverage_type IN ('CARGO_LIABILITY'));

-- Limited to cargo-liability rows. Legacy rows have coverage_type NULL, so this rule never applies to them: they keep
-- their current values and stay updatable. Every row that exists before this migration has coverage_type NULL, so the
-- check is validated at once and no existing row can break it.
ALTER TABLE insurance_infos
    ADD CONSTRAINT chk_insurance_verified_stamp
    CHECK (coverage_type IS DISTINCT FROM 'CARGO_LIABILITY'
        OR status IS DISTINCT FROM 'VERIFIED'
        OR (verified_by IS NOT NULL AND verified_at IS NOT NULL));

CREATE INDEX idx_insurance_infos_coverage_status ON insurance_infos (coverage_type, status);

-- Rollback (manual):
-- DROP INDEX idx_insurance_infos_coverage_status;
-- ALTER TABLE insurance_infos DROP CONSTRAINT chk_insurance_verified_stamp, DROP CONSTRAINT chk_insurance_coverage_type;
-- ALTER TABLE insurance_infos DROP COLUMN coverage_type, DROP COLUMN certificate_key, DROP COLUMN submitted_at,
--     DROP COLUMN verified_by, DROP COLUMN verified_at, DROP COLUMN rejection_reason;
