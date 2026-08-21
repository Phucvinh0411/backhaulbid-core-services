CREATE TABLE empty_routes (
    id UUID PRIMARY KEY,
    truck_id VARCHAR(255) NOT NULL,
    company_id VARCHAR(255) NOT NULL,
    expected_empty_time TIMESTAMP NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) NOT NULL
);

ALTER TABLE driver_profiles ADD COLUMN reviewed_at TIMESTAMP;
