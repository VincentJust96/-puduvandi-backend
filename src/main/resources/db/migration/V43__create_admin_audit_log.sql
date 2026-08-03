-- ============================================================
-- V43__create_admin_audit_log.sql
-- Queryable audit trail for admin/superadmin destructive actions (suspend,
-- delete, approve/reject, raw DB edits via the super-admin data console).
-- Previously these only ever hit log.warn/log.info — not queryable, so
-- "who changed this row and when" had no answer from the product itself.
-- ============================================================

CREATE TABLE admin_audit_log (
    id                BIGSERIAL PRIMARY KEY,
    actor_user_id     BIGINT NOT NULL REFERENCES users(id),
    actor_role        VARCHAR(20) NOT NULL,
    action            VARCHAR(100) NOT NULL,
    entity_type       VARCHAR(50) NOT NULL,
    entity_id         VARCHAR(50),
    before_state      JSONB,
    after_state       JSONB,
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_log_actor      ON admin_audit_log(actor_user_id);
CREATE INDEX idx_admin_audit_log_entity     ON admin_audit_log(entity_type, entity_id);
CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log(created_at);
