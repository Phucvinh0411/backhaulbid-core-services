-- Seed fleet vehicles and driver profiles for Carrier account (55555555-5555-5555-5555-555555555555)

DELETE FROM driver_profiles WHERE carrier_id = '55555555-5555-5555-5555-555555555555';
DELETE FROM vehicles WHERE carrier_id = '55555555-5555-5555-5555-555555555555';

INSERT INTO vehicles (id, carrier_id, license_plate, vehicle_type, body_type, payload_capacity, status)
VALUES
    (
        '55555555-0000-0000-0000-000000000001',
        '55555555-5555-5555-5555-555555555555',
        '29C-12345',
        'TRUCK_MEDIUM',
        'Thùng mui bạt',
        8000.00,
        'VERIFIED'
    ),
    (
        '55555555-0000-0000-0000-000000000002',
        '55555555-5555-5555-5555-555555555555',
        '51D-98765',
        'CONTAINER_TRACTOR',
        'Xe đầu kéo 40 feet',
        32000.00,
        'VERIFIED'
    ),
    (
        '55555555-0000-0000-0000-000000000003',
        '55555555-5555-5555-5555-555555555555',
        '60C-55555',
        'REFRIGERATED_TRUCK',
        'Thùng đông lạnh (-18C)',
        15000.00,
        'VERIFIED'
    );

INSERT INTO driver_profiles (id, carrier_id, full_name, phone, license_number, status, reviewed_by, reviewed_at)
VALUES
    (
        '55555555-1111-0000-0000-000000000001',
        '55555555-5555-5555-5555-555555555555',
        'Lái Xe Nguyễn Văn A',
        '0911223344',
        'GPLX123456',
        'VERIFIED',
        '11111111-1111-1111-1111-111111111111',
        NOW()
    ),
    (
        '55555555-1111-0000-0000-000000000002',
        '55555555-5555-5555-5555-555555555555',
        'Lái Xe Trần Văn B',
        '0911223355',
        'GPLX987654',
        'VERIFIED',
        '11111111-1111-1111-1111-111111111111',
        NOW()
    );
