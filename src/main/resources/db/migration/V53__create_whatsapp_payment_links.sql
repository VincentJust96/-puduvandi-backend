-- ============================================================
-- V53__create_whatsapp_payment_links.sql
-- One row per payment link sent to a WhatsApp customer. The link
-- itself carries id + nonce + HMAC signature, so it works without
-- a login; used_at makes it single-use once the payment succeeds.
-- ============================================================

CREATE TABLE whatsapp_payment_links (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT NOT NULL REFERENCES bookings(id),
    nonce       VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMP NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100)
);

CREATE INDEX idx_whatsapp_payment_links_booking_id ON whatsapp_payment_links(booking_id);

CREATE TRIGGER trg_whatsapp_payment_links_updated_at
    BEFORE UPDATE ON whatsapp_payment_links
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
