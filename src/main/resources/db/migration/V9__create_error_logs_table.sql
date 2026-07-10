-- ============================================================
-- V9__create_error_logs_table.sql
-- Centralized error log table.
-- Every failure (API, service, scheduler) inserts a row here
-- so issues can be diagnosed quickly by error log ID.
-- ============================================================

CREATE TABLE error_logs (
    id              BIGSERIAL PRIMARY KEY,
    error_code      VARCHAR(50),
    error_message   TEXT NOT NULL,
    stack_trace     TEXT,
    source          VARCHAR(200),
    severity        VARCHAR(20)  NOT NULL DEFAULT 'ERROR',
    context         JSONB,
    entity_type     VARCHAR(50),
    entity_id       BIGINT,
    user_id         BIGINT,
    request_path    VARCHAR(500),
    request_method  VARCHAR(10),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_error_logs_severity   ON error_logs(severity);
CREATE INDEX idx_error_logs_entity     ON error_logs(entity_type, entity_id);
CREATE INDEX idx_error_logs_user_id    ON error_logs(user_id);
CREATE INDEX idx_error_logs_created_at ON error_logs(created_at DESC);
