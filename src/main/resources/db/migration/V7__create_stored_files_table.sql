-- ============================================================
-- V7__create_stored_files_table.sql
-- Generic file/blob storage metadata table.
-- Actual bytes live on local disk (Phase 1) or cloud blob store (Phase 2).
-- ============================================================

CREATE TABLE stored_files (
    id                  BIGSERIAL PRIMARY KEY,
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    storage_path        VARCHAR(500) NOT NULL,
    file_url            VARCHAR(500),
    category            VARCHAR(50),
    uploaded_by_user_id BIGINT,
    file_size           BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

CREATE INDEX idx_stored_files_uploaded_by ON stored_files(uploaded_by_user_id);
CREATE INDEX idx_stored_files_category    ON stored_files(category);

CREATE TRIGGER trg_stored_files_updated_at
    BEFORE UPDATE ON stored_files
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
