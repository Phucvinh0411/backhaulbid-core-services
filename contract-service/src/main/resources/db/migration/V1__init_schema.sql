CREATE TABLE trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipper_id UUID NOT NULL,
    carrier_id UUID NOT NULL,
    vehicle_id UUID NOT NULL,
    pickup_location VARCHAR(500) NOT NULL,
    delivery_location VARCHAR(500) NOT NULL,
    agreed_price NUMERIC(19, 2) NOT NULL CHECK (agreed_price >= 0),
    status VARCHAR(30) NOT NULL,
    cancellation_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_trip_status CHECK (status IN ('WAITING_PICKUP', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE tracking_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    status VARCHAR(30) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tracking_status CHECK (status IN ('WAITING_PICKUP', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE delivery_proofs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    note VARCHAR(500),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL UNIQUE REFERENCES trips(id),
    shipper_id UUID NOT NULL,
    carrier_id UUID NOT NULL,
    contract_code VARCHAR(50) NOT NULL UNIQUE,
    pdf_url VARCHAR(500),
    pdf_hash_sha256 VARCHAR(64),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_contract_status CHECK (status IN ('DRAFT', 'WAITING_SIGNATURE', 'SIGNED', 'CANCELLED'))
);

CREATE TABLE contract_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    otp_code_hashed VARCHAR(255),
    signed_at TIMESTAMPTZ,
    CONSTRAINT uk_contract_signer UNIQUE (contract_id, account_id),
    CONSTRAINT chk_contract_signer_role CHECK (role IN ('SHIPPER', 'CARRIER', 'ADMIN'))
);

CREATE INDEX idx_trips_shipper_id ON trips(shipper_id);
CREATE INDEX idx_trips_carrier_id ON trips(carrier_id);
CREATE INDEX idx_trips_vehicle_id ON trips(vehicle_id);
CREATE INDEX idx_tracking_logs_trip_time ON tracking_logs(trip_id, recorded_at DESC);
CREATE INDEX idx_delivery_proofs_trip_id ON delivery_proofs(trip_id);
