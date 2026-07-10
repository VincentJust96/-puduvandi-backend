-- ============================================================
-- V2__create_owner_and_document_tables.sql
-- Puduvandi - Phase 2: Owner Profiles & Documents Schema
-- ============================================================

CREATE TYPE document_type AS ENUM (
    'AADHAAR', 'PAN', 'DRIVING_LICENSE',
    'VEHICLE_RC', 'VEHICLE_INSURANCE', 'BANK_PASSBOOK'
);

CREATE TYPE document_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');

-- ===== USER DOCUMENTS =====

CREATE TABLE user_documents (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_type   document_type NOT NULL,
    document_url    VARCHAR(500) NOT NULL,
    status          document_status NOT NULL DEFAULT 'PENDING',
    remarks         VARCHAR(255),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

CREATE INDEX idx_user_docs_user ON user_documents(user_id);
CREATE INDEX idx_user_docs_type ON user_documents(document_type);

CREATE TRIGGER trg_user_documents_updated_at
    BEFORE UPDATE ON user_documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ===== OWNER PROFILES =====

CREATE TABLE owner_profiles (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    business_name           VARCHAR(150),
    gstin                   VARCHAR(20),
    address_line1           VARCHAR(255),
    address_line2           VARCHAR(255),
    city                    VARCHAR(100),
    state                   VARCHAR(100),
    pincode                 VARCHAR(10),
    bank_account_number     VARCHAR(50),
    bank_ifsc_code          VARCHAR(20),
    bank_name               VARCHAR(100),
    account_holder_name     VARCHAR(100),
    total_bikes             INT NOT NULL DEFAULT 0,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100)
);

CREATE INDEX idx_owner_profiles_user ON owner_profiles(user_id);

CREATE TRIGGER trg_owner_profiles_updated_at
    BEFORE UPDATE ON owner_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ===== OWNER DOCUMENTS =====

CREATE TABLE owner_documents (
    id              BIGSERIAL PRIMARY KEY,
    owner_id        BIGINT NOT NULL REFERENCES owner_profiles(id) ON DELETE CASCADE,
    document_type   document_type NOT NULL,
    document_url    VARCHAR(500) NOT NULL,
    status          document_status NOT NULL DEFAULT 'PENDING',
    remarks         VARCHAR(255),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

CREATE INDEX idx_owner_docs_owner ON owner_documents(owner_id);
CREATE INDEX idx_owner_docs_type  ON owner_documents(document_type);

CREATE TRIGGER trg_owner_documents_updated_at
    BEFORE UPDATE ON owner_documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE user_documents  IS 'Documents uploaded by customers (driving license etc.)';
COMMENT ON TABLE owner_profiles  IS 'Extended KYC profile for OWNER role users';
COMMENT ON TABLE owner_documents IS 'KYC documents uploaded by owners (Aadhaar, PAN etc.)';
