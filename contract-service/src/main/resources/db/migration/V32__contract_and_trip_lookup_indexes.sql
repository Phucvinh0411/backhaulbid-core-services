CREATE INDEX IF NOT EXISTS idx_contracts_shipper_created ON contracts (shipper_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_contracts_carrier_created ON contracts (carrier_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_contract_signatures_contract_id ON contract_signatures (contract_id);
CREATE INDEX IF NOT EXISTS idx_trips_shipper_created ON trips (shipper_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_trips_carrier_created ON trips (carrier_id, created_at DESC);
