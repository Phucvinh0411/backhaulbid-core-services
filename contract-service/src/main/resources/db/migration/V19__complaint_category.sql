-- Complaint classification. Existing rows stay NULL ("chưa phân loại"):
-- there is no reliable basis to infer a category for legacy complaints.
-- Manual rollback (Flyway Community has no undo):
--   DROP INDEX IF EXISTS idx_complaints_category_created_at;
--   ALTER TABLE complaints DROP CONSTRAINT IF EXISTS chk_complaint_category;
--   ALTER TABLE complaints DROP COLUMN IF EXISTS category;
--   DELETE FROM flyway_schema_history WHERE version = '19';
ALTER TABLE complaints
    ADD COLUMN category VARCHAR(40);

ALTER TABLE complaints
    ADD CONSTRAINT chk_complaint_category CHECK (
        category IS NULL OR category IN (
            'CARGO_DAMAGE_LOSS',
            'CARGO_INFO_MISMATCH',
            'SCHEDULE_DELAY',
            'VEHICLE_DRIVER_ISSUE',
            'DELIVERY_CONFIRMATION',
            'PAYMENT_DEPOSIT',
            'CANCELLATION',
            'CONDUCT',
            'OTHER'
        )
    );

CREATE INDEX idx_complaints_category_created_at ON complaints(category, created_at DESC);
