-- V7: Seed đội xe chuẩn hóa (VERIFIED) và khai báo chuyến xe rỗng chiều cho Nhà xe (1234567899 / 55555555-5555-5555-5555-555555555555)

DELETE FROM empty_routes WHERE company_id = '55555555-5555-5555-5555-555555555555' OR truck_id IN ('51C-888.88', '51D-999.99', '50H-123.45', '51E-777.77', '29C-12345', '51D-98765', '60C-55555');
DELETE FROM driver_profiles WHERE carrier_id = '55555555-5555-5555-5555-555555555555';
DELETE FROM vehicles WHERE carrier_id = '55555555-5555-5555-5555-555555555555';

INSERT INTO vehicles (id, carrier_id, license_plate, vehicle_type, body_type, payload_capacity, status)
VALUES
    (
        '55555555-0000-0000-0000-000000000001',
        '55555555-5555-5555-5555-555555555555',
        '51C-888.88',
        'TRUCK_MEDIUM',
        'Thùng mui bạt',
        8000.00,
        'VERIFIED'
    ),
    (
        '55555555-0000-0000-0000-000000000002',
        '55555555-5555-5555-5555-555555555555',
        '51D-999.99',
        'CONTAINER_TRACTOR',
        'Xe đầu kéo 40 feet',
        32000.00,
        'VERIFIED'
    ),
    (
        '55555555-0000-0000-0000-000000000003',
        '55555555-5555-5555-5555-555555555555',
        '50H-123.45',
        'REFRIGERATED_TRUCK',
        'Thùng đông lạnh (-18°C)',
        15000.00,
        'VERIFIED'
    ),
    (
        '55555555-0000-0000-0000-000000000004',
        '55555555-5555-5555-5555-555555555555',
        '51E-777.77',
        'TRUCK_HEAVY',
        'Thùng kín 9.5m',
        16000.00,
        'VERIFIED'
    );

INSERT INTO driver_profiles (id, carrier_id, full_name, phone, license_number, status, reviewed_by, reviewed_at)
VALUES
    (
        '55555555-1111-0000-0000-000000000001',
        '55555555-5555-5555-5555-555555555555',
        'Lái Xe Nguyễn Văn Tài',
        '0911223344',
        'GPLX123456',
        'VERIFIED',
        '11111111-1111-1111-1111-111111111111',
        NOW()
    ),
    (
        '55555555-1111-0000-0000-000000000002',
        '55555555-5555-5555-5555-555555555555',
        'Lái Xe Trần Văn Thắng',
        '0911223355',
        'GPLX987654',
        'VERIFIED',
        '11111111-1111-1111-1111-111111111111',
        NOW()
    );

INSERT INTO empty_routes (id, truck_id, company_id, expected_empty_time, latitude, longitude, origin, destination, status)
VALUES
    (
        '55555555-e001-0000-0000-000000000001',
        '51C-888.88',
        '55555555-5555-5555-5555-555555555555',
        NOW() + INTERVAL '2 days',
        10.8231,
        106.6297,
        'TP. Hồ Chí Minh',
        'Hà Nội',
        'PENDING'
    );
