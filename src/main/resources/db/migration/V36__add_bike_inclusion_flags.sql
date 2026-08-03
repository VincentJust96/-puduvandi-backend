-- ============================================================
-- V36__add_bike_inclusion_flags.sql
-- The "What's included" chips on the bike detail page (papers, fuel,
-- roadside help) were hardcoded on the frontend for every bike. The owner
-- can now choose these per bike, same as the existing helmet_included flag.
-- Default true so existing bikes keep showing the same chips as before.
-- ============================================================

ALTER TABLE bikes ADD COLUMN papers_included BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE bikes ADD COLUMN fuel_included BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE bikes ADD COLUMN roadside_assistance BOOLEAN NOT NULL DEFAULT true;
