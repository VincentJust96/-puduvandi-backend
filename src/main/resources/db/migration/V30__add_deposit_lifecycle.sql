-- ============================================================
-- V29__add_deposit_lifecycle.sql
-- Security deposits were previously just a number folded into a booking's
-- total_amount, collected upfront like rent and never resolved. This adds
-- the missing lifecycle: a deposit stays HELD after a booking completes,
-- the owner can file a deduction claim (reviewed by an admin — owners never
-- get unilateral power over a customer's money), and an unclaimed deposit
-- auto-releases after a grace period (see DepositReleaseTask).
-- ============================================================

ALTER TABLE bookings
    ADD COLUMN deposit_status        VARCHAR(20) NOT NULL DEFAULT 'HELD',
    ADD COLUMN deposit_refund_amount NUMERIC(10,2),
    ADD COLUMN deposit_refunded_at   TIMESTAMP;

CREATE INDEX idx_bookings_deposit_status ON bookings(deposit_status);

CREATE TABLE deposit_claims (
    id                      BIGSERIAL PRIMARY KEY,
    booking_id              BIGINT NOT NULL REFERENCES bookings(id),
    filed_by_owner_id       BIGINT NOT NULL REFERENCES users(id),
    deduction_amount        NUMERIC(10,2) NOT NULL,
    reason                  VARCHAR(500) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_rejection_reason  VARCHAR(500),
    decided_by_admin_id     BIGINT REFERENCES users(id),
    decided_at              TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100)
);

CREATE INDEX idx_deposit_claims_booking ON deposit_claims(booking_id);
CREATE INDEX idx_deposit_claims_status  ON deposit_claims(status);
