-- ============================================================
-- V46__add_trip_cover_image.sql
-- Optional uploaded background image for a trip's dashboard hero
-- banner (see TripDashboardResponse.coverImageUrl).
-- ============================================================

ALTER TABLE trips ADD COLUMN cover_image_url VARCHAR(500);
