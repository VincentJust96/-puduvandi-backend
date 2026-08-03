-- ============================================================
-- V42__add_missing_fk_indexes_and_deposit_claim_uniqueness.sql
-- Closes two gaps found in an investor-demo-readiness audit:
--   1. reviews.customer_id and bike_condition_reviews.customer_id have no
--      index (unlike bookings.customer_id/owner_id, which already do —
--      see V3) — a "my reviews"/"my condition reviews" lookup by customer
--      would full-scan as data grows.
--   2. deposit_claims has no DB-level guard against two PENDING claims on
--      the same booking. The application already prevents this correctly
--      (DepositClaimService.fileClaim locks the booking row and checks
--      depositStatus == HELD before allowing a new claim), so this is
--      belt-and-suspenders, not a fix for an observed bug.
-- ============================================================

CREATE INDEX idx_reviews_customer ON reviews(customer_id);
CREATE INDEX idx_bike_condition_reviews_customer ON bike_condition_reviews(customer_id);

CREATE UNIQUE INDEX idx_deposit_claims_one_pending_per_booking
    ON deposit_claims(booking_id)
    WHERE status = 'PENDING';
