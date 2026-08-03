-- ============================================================
-- V37__add_bike_document_fields.sql
-- Owner-uploaded RC (registration certificate) and insurance documents,
-- visible directly to customers on the bike listing so they can confirm
-- the bike is properly documented. Self-declared by the owner — no admin
-- review step. All nullable since existing bikes won't have these yet.
-- ============================================================

ALTER TABLE bikes ADD COLUMN rc_document_url VARCHAR(500);
ALTER TABLE bikes ADD COLUMN insurance_document_url VARCHAR(500);
ALTER TABLE bikes ADD COLUMN insurance_policy_number VARCHAR(100);
ALTER TABLE bikes ADD COLUMN insurance_expiry_date DATE;
