-- ============================================================
-- V28__create_whatsapp_tables.sql
-- WhatsApp booking bot: per-phone conversation state, and a
-- de-duplication table for inbound webhook messages (Meta may
-- deliver the same message more than once).
-- ============================================================

CREATE TABLE whatsapp_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    wa_id               VARCHAR(20) NOT NULL UNIQUE,
    state               VARCHAR(30) NOT NULL,
    area                VARCHAR(150),
    rental_mode         VARCHAR(10),
    pickup_datetime     TIMESTAMP,
    return_datetime     TIMESTAMP,
    bike_id             BIGINT,
    last_interaction_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

CREATE TRIGGER trg_whatsapp_sessions_updated_at
    BEFORE UPDATE ON whatsapp_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE whatsapp_processed_messages (
    id          BIGSERIAL PRIMARY KEY,
    message_id  VARCHAR(200) NOT NULL UNIQUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100)
);

CREATE INDEX idx_whatsapp_processed_messages_created_at ON whatsapp_processed_messages(created_at);

CREATE TRIGGER trg_whatsapp_processed_messages_updated_at
    BEFORE UPDATE ON whatsapp_processed_messages
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
