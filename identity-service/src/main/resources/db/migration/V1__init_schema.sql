-- ============================================
-- V1__init_schema.sql
-- Identity Service - Initial Schema
-- ============================================
-- Constraints:
--   1. Role is a PostgreSQL ENUM type (SHIPPER, CARRIER)
--   2. Each account holds exactly ONE role
--   3. UUID as primary key
--   4. No Base Entity inheritance (no redundant fields)
-- ============================================

-- Step 1: Create ENUM type for account roles
CREATE TYPE account_role AS ENUM ('SHIPPER', 'CARRIER');

-- Step 2: Create accounts table
CREATE TABLE accounts (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(150)    NOT NULL,
    phone           VARCHAR(20),
    role            account_role    NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Step 3: Create indexes for frequently queried columns
CREATE INDEX idx_accounts_email ON accounts (email);
CREATE INDEX idx_accounts_role ON accounts (role);
CREATE INDEX idx_accounts_phone ON accounts (phone);

-- Step 4: Add comment for documentation
COMMENT ON TABLE accounts IS 'User accounts for BackHaulBid platform - each account has exactly one role';
COMMENT ON COLUMN accounts.role IS 'Account role: SHIPPER (freight owner) or CARRIER (transport provider)';
COMMENT ON TYPE account_role IS 'Enum defining allowed roles: SHIPPER, CARRIER';
