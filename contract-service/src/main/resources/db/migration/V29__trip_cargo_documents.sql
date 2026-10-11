-- V3.3 cargo value documents: when an award attempt creates a trip, the shipper's declared goods value and the typed
-- value-document references are copied onto that trip. The copy is the trip's record; later changes to the auction
-- do not reach it. Documents added after the award are kept as TRIP_SUPPLEMENT rows with who added them and when.
-- Additive only. Trips created before this migration keep NULL and show no snapshot; nothing is backfilled.

ALTER TABLE trips
    ADD COLUMN declared_goods_value NUMERIC(19,2) CHECK (declared_goods_value IS NULL OR declared_goods_value >= 0),
    ADD COLUMN cargo_snapshot_hash VARCHAR(64);

CREATE TABLE trip_cargo_documents (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    document_id VARCHAR(36) NOT NULL CHECK (document_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('INVOICE', 'RECEIPT', 'CONTRACT', 'PAYMENT_PROOF', 'OTHER', 'UNCLASSIFIED')),
    source VARCHAR(20) NOT NULL CHECK (source IN ('AWARD_SNAPSHOT', 'TRIP_SUPPLEMENT')),
    -- The private object key only. It is never returned; clients get the proxy path.
    object_key VARCHAR(500) NOT NULL CHECK (
        object_key ~ '^goods-value-docs/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(pdf|png|jpe?g|webp|gif)$'
        AND object_key LIKE 'goods-value-docs/' || document_id || '.%'),
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_trip_cargo_document_id UNIQUE (trip_id, document_id),
    CONSTRAINT uq_trip_cargo_document_key UNIQUE (trip_id, object_key)
);

CREATE INDEX idx_trip_cargo_documents_trip ON trip_cargo_documents(trip_id, uploaded_at);
