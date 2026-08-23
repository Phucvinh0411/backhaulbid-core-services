-- Seed requested Shipper (1234567890) and Carrier (1234567899) accounts with UTF-8 Vietnamese text

DELETE FROM ekyc_verifications WHERE account_id IN ('44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555');
DELETE FROM companies WHERE account_id IN ('44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555');
DELETE FROM user_profiles WHERE account_id IN ('44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555');
DELETE FROM accounts WHERE id IN ('44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555');

INSERT INTO accounts (id, phone, email, password_hash, role, status)
VALUES
    (
        '44444444-4444-4444-4444-444444444444',
        '1234567890',
        'shipper123@backhaulbid.local',
        '$2a$10$aNYIyREfysTSk6icZRoKueuG69mGIMRsnZFsxv8mZ39Sv6AMmdWk2',
        'SHIPPER',
        'ACTIVE'
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        '1234567899',
        'carrier123@backhaulbid.local',
        '$2a$10$aNYIyREfysTSk6icZRoKueuG69mGIMRsnZFsxv8mZ39Sv6AMmdWk2',
        'CARRIER',
        'ACTIVE'
    );

INSERT INTO user_profiles (account_id, full_name, address)
VALUES
    (
        '44444444-4444-4444-4444-444444444444',
        'Nguyễn Văn Hàng (Shipper)',
        '123 Lê Lợi, Quận 1, TP. Hồ Chí Minh'
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        'Trần Văn Xe (Carrier)',
        '456 Phạm Văn Đồng, Cầu Giấy, Hà Nội'
    );

INSERT INTO companies (id, account_id, tax_code, company_name, address, verification_status, company_status)
VALUES
    (
        '44444444-4444-4444-4444-444444440000',
        '44444444-4444-4444-4444-444444444444',
        '1234567890-001',
        'Công Ty TNHH Shipper Việt Nam',
        '123 Lê Lợi, Quận 1, TP. Hồ Chí Minh',
        'VERIFIED',
        'ACTIVE'
    ),
    (
        '55555555-5555-5555-5555-555555550000',
        '55555555-5555-5555-5555-555555555555',
        '1234567899-001',
        'Công Ty Vận Tải Hàng Xe Việt Nam',
        '456 Phạm Văn Đồng, Cầu Giấy, Hà Nội',
        'VERIFIED',
        'ACTIVE'
    );

INSERT INTO ekyc_verifications (account_id, identity_number, status, liveness_passed, face_match_score)
VALUES
    (
        '44444444-4444-4444-4444-444444444444',
        '079200012345',
        'VERIFIED',
        true,
        98.5
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        '079200099999',
        'VERIFIED',
        true,
        99.0
    );
