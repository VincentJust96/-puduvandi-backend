-- ============================================================
-- V19__create_partner_profiles_and_documents.sql
-- Delivery Partner onboarding: profile + KYC documents.
-- Mirrors owner_profiles/owner_documents (V2) and the shared
-- users.kyc_status column for the admin approval gate, exactly
-- like the owner KYC flow. document_type/document_status are
-- plain VARCHAR, not the custom enum types V2 originally used —
-- those were dropped in V5 in favour of VARCHAR everywhere.
-- ============================================================

CREATE TABLE partner_profiles (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    vehicle_type            VARCHAR(50),
    vehicle_number          VARCHAR(20),
    city                    VARCHAR(100),
    bank_account_number     VARCHAR(50),
    bank_ifsc_code          VARCHAR(20),
    bank_name               VARCHAR(100),
    account_holder_name     VARCHAR(100),
    total_deliveries        INT NOT NULL DEFAULT 0,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100)
);

CREATE INDEX idx_partner_profiles_user ON partner_profiles(user_id);

CREATE TRIGGER trg_partner_profiles_updated_at
    BEFORE UPDATE ON partner_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE partner_documents (
    id              BIGSERIAL PRIMARY KEY,
    partner_id      BIGINT NOT NULL REFERENCES partner_profiles(id) ON DELETE CASCADE,
    document_type   VARCHAR(50) NOT NULL,
    document_url    VARCHAR(500) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    remarks         VARCHAR(255),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

CREATE INDEX idx_partner_docs_partner ON partner_documents(partner_id);
CREATE INDEX idx_partner_docs_type    ON partner_documents(document_type);

CREATE TRIGGER trg_partner_documents_updated_at
    BEFORE UPDATE ON partner_documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE partner_profiles  IS 'Extended profile for PARTNER role users (delivery partners)';
COMMENT ON TABLE partner_documents IS 'KYC documents uploaded by delivery partners (licence, vehicle RC etc.)';
