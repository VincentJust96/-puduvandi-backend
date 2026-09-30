-- ============================================================
-- V49__add_trip_member_contribution.sql
-- Tracks how much each traveler has actually handed over toward
-- the trip's shared budget (separate from what they've paid for
-- individual expenses) — lets the organizer see who still owes
-- their upfront share and update it once they're paid up.
-- ============================================================

ALTER TABLE trip_members
    ADD COLUMN contributed_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
