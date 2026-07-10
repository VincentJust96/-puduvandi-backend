-- ============================================================
-- V15__create_notification_logs.sql
-- Audit trail for booking SMS/WhatsApp notifications sent via Twilio.
-- One row per send attempt per channel.
-- ============================================================

CREATE TABLE notification_logs (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT NOT NULL,
    customer_phone      VARCHAR(20) NOT NULL,
    message_content     TEXT,
    notification_type   VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    twilio_sid          VARCHAR(100),
    error_message       TEXT,
    sent_at             TIMESTAMP,
    retry_count         INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

CREATE INDEX idx_notification_logs_booking_id  ON notification_logs(booking_id);
CREATE INDEX idx_notification_logs_status      ON notification_logs(status);
CREATE INDEX idx_notification_logs_created_at  ON notification_logs(created_at DESC);

CREATE TRIGGER trg_notification_logs_updated_at
    BEFORE UPDATE ON notification_logs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
