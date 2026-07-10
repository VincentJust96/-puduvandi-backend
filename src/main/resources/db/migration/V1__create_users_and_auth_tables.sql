-- ============================================================
-- V1__create_users_and_auth_tables.sql
-- Puduvandi - Phase 1: Auth Schema
-- ============================================================

-- ===== ENUM TYPES =====

CREATE TYPE user_role AS ENUM ('CUSTOMER', 'OWNER', 'ADMIN');
CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'PENDING_VERIFICATION');
CREATE TYPE kyc_status AS ENUM ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED');

-- ===== USERS TABLE =====

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    phone_number        VARCHAR(15) NOT NULL UNIQUE,
    email               VARCHAR(100),
    full_name           VARCHAR(100),
    role                user_role NOT NULL DEFAULT 'CUSTOMER',
    status              user_status NOT NULL DEFAULT 'PENDING_VERIFICATION',
    kyc_status          kyc_status NOT NULL DEFAULT 'NOT_SUBMITTED',
    profile_image_url   VARCHAR(500),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

CREATE INDEX idx_users_phone     ON users(phone_number);
CREATE INDEX idx_users_role      ON users(role);
CREATE INDEX idx_users_status    ON users(status);
CREATE INDEX idx_users_deleted   ON users(is_deleted);

COMMENT ON TABLE  users              IS 'All platform users: customers, owners, admins';
COMMENT ON COLUMN users.phone_number IS 'Primary login identifier - must be unique';
COMMENT ON COLUMN users.role         IS 'CUSTOMER | OWNER | ADMIN';
COMMENT ON COLUMN users.status       IS 'Account lifecycle status';
COMMENT ON COLUMN users.kyc_status   IS 'KYC verification state for owners';

-- ===== OTP TABLE =====

CREATE TABLE otp_records (
    id              BIGSERIAL PRIMARY KEY,
    phone_number    VARCHAR(15) NOT NULL,
    otp_code        VARCHAR(10) NOT NULL,
    purpose         VARCHAR(50) NOT NULL DEFAULT 'LOGIN',   -- LOGIN | REGISTER
    is_used         BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_phone      ON otp_records(phone_number);
CREATE INDEX idx_otp_expires    ON otp_records(expires_at);

COMMENT ON TABLE otp_records IS 'OTP records for phone-based authentication';

-- ===== REFRESH TOKENS TABLE =====

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(512) NOT NULL UNIQUE,
    device_info     VARCHAR(255),
    ip_address      VARCHAR(50),
    expires_at      TIMESTAMP NOT NULL,
    is_revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_token_user    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_token_token   ON refresh_tokens(token);
CREATE INDEX idx_refresh_token_expires ON refresh_tokens(expires_at);

COMMENT ON TABLE refresh_tokens IS 'JWT refresh tokens - supports multiple devices per user';

-- ===== AUTO UPDATE updated_at TRIGGER =====

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
