CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    carrier_id UUID NOT NULL,
    license_plate VARCHAR(30) NOT NULL UNIQUE,
    vehicle_type VARCHAR(40) NOT NULL,
    body_type VARCHAR(100),
    payload_capacity NUMERIC(12, 2) NOT NULL CHECK (payload_capacity >= 0),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vehicle_type CHECK (vehicle_type IN ('TRUCK_SMALL', 'TRUCK_MEDIUM', 'TRUCK_HEAVY', 'CONTAINER_TRACTOR', 'REFRIGERATED_TRUCK', 'SPECIALIZED_TRUCK')),
    CONSTRAINT chk_vehicle_status CHECK (status IN ('DRAFT', 'PENDING', 'VERIFIED', 'REJECTED', 'INACTIVE'))
);

CREATE TABLE driver_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    carrier_id UUID NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    license_number VARCHAR(50) NOT NULL UNIQUE,
    license_image_url VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_driver_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'))
);

CREATE TABLE vehicle_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL UNIQUE REFERENCES vehicles(id) ON DELETE CASCADE,
    verified_by UUID,
    status VARCHAR(30) NOT NULL,
    note VARCHAR(500),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vehicle_verification_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'))
);

CREATE TABLE vehicle_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    document_type VARCHAR(40) NOT NULL,
    document_url VARCHAR(500) NOT NULL,
    expired_date DATE,
    status VARCHAR(30) NOT NULL,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vehicle_document_type CHECK (document_type IN ('REGISTRATION', 'INSPECTION_CERTIFICATE', 'LIABILITY_INSURANCE')),
    CONSTRAINT chk_vehicle_document_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'))
);

CREATE INDEX idx_vehicles_carrier_id ON vehicles(carrier_id);
CREATE INDEX idx_vehicles_status_type ON vehicles(status, vehicle_type);
CREATE INDEX idx_driver_profiles_carrier_id ON driver_profiles(carrier_id);
CREATE INDEX idx_vehicle_documents_vehicle_id ON vehicle_documents(vehicle_id);
