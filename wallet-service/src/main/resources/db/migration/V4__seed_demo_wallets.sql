-- Seed wallets for Shipper (1234567890) and Carrier (1234567899) accounts

DELETE FROM transactions WHERE wallet_id IN ('44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555');
DELETE FROM wallets WHERE id IN ('44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555');

INSERT INTO wallets (id, account_id, balance, frozen_balance)
VALUES
    (
        '44444444-4444-4444-4444-444444444444',
        '44444444-4444-4444-4444-444444444444',
        50000000.00,
        0.00
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        '55555555-5555-5555-5555-555555555555',
        20000000.00,
        0.00
    );

INSERT INTO transactions (wallet_id, amount, type, status, payment_method, reference_code, description)
VALUES
    (
        '44444444-4444-4444-4444-444444444444',
        50000000.00,
        'DEPOSIT',
        'SUCCESS',
        'BANK_TRANSFER',
        'INIT-SHIPPER-001',
        'Nap tien khoi tao tai khoan Chu hang Demo'
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        20000000.00,
        'DEPOSIT',
        'SUCCESS',
        'BANK_TRANSFER',
        'INIT-CARRIER-001',
        'Nap tien khoi tao tai khoan Nha xe Demo'
    );
