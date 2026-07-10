-- ============================================================
-- V4__create_commission_settings_table.sql
-- Puduvandi - Phase 4: Admin - Commission Settings
-- ============================================================

CREATE TABLE commission_settings (
    id                  BIGSERIAL PRIMARY KEY,
    commission_percent  NUMERIC(5, 2) NOT NULL DEFAULT 20.00,
    updated_by_admin_id BIGINT REFERENCES users(id),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_commission_settings_updated_at
    BEFORE UPDATE ON commission_settings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Seed the default commission row (single-row settings pattern)
INSERT INTO commission_settings (commission_percent, is_active)
VALUES (20.00, TRUE);

COMMENT ON TABLE commission_settings IS 'Single-row table storing the platform commission %. Updated by Admin.';
