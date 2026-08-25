-- ============================================================
-- V47__add_trip_cover_image_position.sql
-- Vertical focal point (0-100, 50 = center) for the trip cover
-- image, so a photo can be repositioned instead of always being
-- center-cropped by the hero banner's fixed aspect ratio.
-- ============================================================

ALTER TABLE trips ADD COLUMN cover_image_position_y SMALLINT NOT NULL DEFAULT 50;
