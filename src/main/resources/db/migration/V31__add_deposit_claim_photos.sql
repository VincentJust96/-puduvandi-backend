-- ============================================================
-- V31__add_deposit_claim_photos.sql
-- Optional damage-evidence photos on a deposit claim — a short,
-- comma-joined list of file URLs (uploaded via the existing
-- /api/v1/files/upload endpoint), not a separate join table since
-- the list is small and short-lived, unlike bike listing photos.
-- ============================================================

ALTER TABLE deposit_claims ADD COLUMN photo_urls VARCHAR(2000);
