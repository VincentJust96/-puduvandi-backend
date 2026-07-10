-- ============================================================
-- V8__add_helmet_included_to_bookings_and_nullable_transmission.sql
-- 1. Add helmet_included to bookings (copied from bike at booking time).
-- 2. Make bikes.transmission nullable so owners don't have to specify it.
-- ============================================================

ALTER TABLE bookings
    ADD COLUMN helmet_included BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE bikes
    ALTER COLUMN transmission DROP NOT NULL;
