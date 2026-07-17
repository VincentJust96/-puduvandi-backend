-- ============================================================
-- V18__create_handover_otps.sql
-- In-app OTP handover system for bike pickup/return between
-- customer, owner, and delivery partner. Append-only audit trail
-- (except used/used_at/verified_by_user_id/failed_attempts).
-- Also adds RETURN_COLLECTED / RETURN_COMPLETED milestones to
-- delivery_orders for the partner-delivery return flow.
-- ============================================================

CREATE TABLE handover_otps (
    id                    BIGSERIAL PRIMARY KEY,
    booking_id            BIGINT NOT NULL REFERENCES bookings(id),
    purpose               VARCHAR(30) NOT NULL,
    otp_code              VARCHAR(10) NOT NULL,
    generated_by_user_id  BIGINT NOT NULL,
    expires_at            TIMESTAMP NOT NULL,
    used                  BOOLEAN NOT NULL DEFAULT false,
    used_at               TIMESTAMP,
    verified_by_user_id   BIGINT,
    failed_attempts       INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_handover_otps_booking_purpose ON handover_otps(booking_id, purpose);
CREATE INDEX idx_handover_otps_active_lookup   ON handover_otps(booking_id, purpose, used, expires_at);

-- ===== Partner-delivery return milestones =====

ALTER TABLE delivery_orders ADD COLUMN return_collected_at  TIMESTAMP;
ALTER TABLE delivery_orders ADD COLUMN return_completed_at  TIMESTAMP;
