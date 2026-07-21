-- ============================================
-- V1__init_schema.sql
-- Identity Service - Initial Database Schema
-- Matches Domain Class Diagram & JPA Entities
-- ============================================

-- 1. Create accounts table
CREATE TABLE accounts (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    phone           VARCHAR(20)     NOT NULL UNIQUE,
    email           VARCHAR(255)    UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    role            VARCHAR(50)     NOT NULL CONSTRAINT chk_accounts_role CHECK (role IN ('ADMIN', 'SHIPPER', 'CARRIER', 'DRIVER')),
    status          VARCHAR(50)     NOT NULL DEFAULT 'IN_ACTIVE' CONSTRAINT chk_accounts_status CHECK (status IN ('IN_ACTIVE', 'ACTIVE', 'BLOCKED')),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 2. Create user_profiles table (1-1 with accounts)
CREATE TABLE user_profiles (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID            NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE CASCADE,
    full_name       VARCHAR(150),
    address         VARCHAR(255),
    avatar_url      VARCHAR(500),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 3. Create companies table (1-1 with accounts)
CREATE TABLE companies (
    id                     UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id             UUID            NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE CASCADE,
    tax_code               VARCHAR(50)     NOT NULL UNIQUE,
    company_name           VARCHAR(255),
    address                VARCHAR(255),
    business_license_url   VARCHAR(500),
    transport_license_url  VARCHAR(500),
    verification_status    VARCHAR(50)     CONSTRAINT chk_companies_verification_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')),
    company_status         VARCHAR(50)     CONSTRAINT chk_companies_status CHECK (company_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DISSOLVED')),
    created_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 4. Create ekyc_verifications table (1-1 with accounts)
CREATE TABLE ekyc_verifications (
    id                 UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id         UUID             NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE CASCADE,
    identity_number    VARCHAR(50),
    front_image_url    VARCHAR(500),
    back_image_url     VARCHAR(500),
    selfie_image_url   VARCHAR(500),
    liveness_video_url VARCHAR(500),
    face_match_score   DOUBLE PRECISION,
    liveness_passed    BOOLEAN,
    status             VARCHAR(50)      CONSTRAINT chk_ekyc_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')),
    created_at         TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

-- 5. Create insurance_infos table (1-N with companies)
CREATE TABLE insurance_infos (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID            NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    provider_name   VARCHAR(255),
    policy_number   VARCHAR(100),
    coverage_limit  NUMERIC(15, 2),
    expired_date    DATE,
    status          VARCHAR(50)     CONSTRAINT chk_insurance_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 6. Indexes for optimize performance
CREATE INDEX idx_accounts_phone ON accounts (phone);
CREATE INDEX idx_accounts_email ON accounts (email);
CREATE INDEX idx_accounts_role ON accounts (role);
CREATE INDEX idx_accounts_status ON accounts (status);

CREATE INDEX idx_user_profiles_account_id ON user_profiles (account_id);

CREATE INDEX idx_companies_account_id ON companies (account_id);
CREATE INDEX idx_companies_tax_code ON companies (tax_code);

CREATE INDEX idx_ekyc_verifications_account_id ON ekyc_verifications (account_id);
CREATE INDEX idx_ekyc_verifications_identity_number ON ekyc_verifications (identity_number);

CREATE INDEX idx_insurance_infos_company_id ON insurance_infos (company_id);

-- 7. Documentation comments
COMMENT ON TABLE accounts IS 'User accounts for BackHaulBid platform';
COMMENT ON TABLE user_profiles IS 'User personal profile details';
COMMENT ON TABLE companies IS 'Carrier or Shipper business entity details';
COMMENT ON TABLE ekyc_verifications IS 'Identity verification (eKYC) details';
COMMENT ON TABLE insurance_infos IS 'Insurance policies associated with companies';
