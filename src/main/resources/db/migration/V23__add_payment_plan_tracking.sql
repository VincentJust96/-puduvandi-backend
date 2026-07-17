-- ============================================================
-- V23__add_payment_plan_tracking.sql
-- Supports two payment plans at booking time: DEPOSIT (10% now, balance
-- before pickup) and FULL (100% now). A later BALANCE payment clears the
-- remainder on a DEPOSIT-plan booking.
-- ============================================================

ALTER TABLE bookings ADD COLUMN amount_paid NUMERIC(10,2) NOT NULL DEFAULT 0;

ALTER TABLE payments ADD COLUMN payment_type VARCHAR(20) NOT NULL DEFAULT 'FULL';
ALTER TABLE payments ALTER COLUMN payment_type DROP DEFAULT;
