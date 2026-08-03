-- Local/demo data only. This migration is enabled by docker-compose via
-- SPRING_FLYWAY_LOCATIONS and is not part of the default production path.

INSERT INTO accounts (id, phone, email, password_hash, role, status)
VALUES
    (
        '11111111-1111-1111-1111-111111111111',
        '0900000001',
        'admin.demo@backhaulbid.local',
        '$2a$12$3gbMBUKppSnLvuI2hGPYGuXZk9s.qG.mpMf7NJMLaQ0NxKKSURBIS',
        'ADMIN',
        'ACTIVE'
    ),
    (
        '22222222-2222-2222-2222-222222222222',
        '0900000002',
        'carrier.demo@backhaulbid.local',
        '$2a$12$VQXVqt7PYXGU6qGappC/0eGyITzHt3awp8qJE5HVvyNRgcR9m2hxy',
        'CARRIER',
        'ACTIVE'
    ),
    (
        '33333333-3333-3333-3333-333333333333',
        '0900000003',
        'shipper.demo@backhaulbid.local',
        '$2a$12$TjHnYG4TsvY./VYjnG72L.EPi.fswSxMNjf.zzoAzgDIst7L5/JLy',
        'SHIPPER',
        'ACTIVE'
    )
ON CONFLICT (phone) DO NOTHING;

INSERT INTO user_profiles (account_id, full_name, address)
SELECT accounts.id, demo_profiles.full_name, demo_profiles.address
FROM (
    VALUES
        ('0900000001', 'Demo Admin', 'BackHaulBid Local'),
        ('0900000002', 'Demo Carrier', 'BackHaulBid Local'),
        ('0900000003', 'Demo Shipper', 'BackHaulBid Local')
) AS demo_profiles(phone, full_name, address)
JOIN accounts ON accounts.phone = demo_profiles.phone
ON CONFLICT (account_id) DO NOTHING;
