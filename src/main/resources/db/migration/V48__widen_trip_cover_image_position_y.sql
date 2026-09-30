-- ============================================================
-- V48__widen_trip_cover_image_position_y.sql
-- V47 created trips.cover_image_position_y as SMALLINT, but the
-- entity field (Trip.coverImagePositionY) is a plain Integer, which
-- Hibernate maps to INTEGER — the mismatch fails schema validation
-- on startup. Widen the column to match the entity; V47 itself is
-- already applied, so it can't be edited in place.
-- ============================================================

ALTER TABLE trips ALTER COLUMN cover_image_position_y TYPE INTEGER;
