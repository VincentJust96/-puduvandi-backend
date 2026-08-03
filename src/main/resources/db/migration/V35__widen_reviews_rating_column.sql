-- ============================================================
-- V35__widen_reviews_rating_column.sql
-- V34 created reviews.rating as SMALLINT, but the JPA entity maps it as a
-- plain Integer (Hibernate expects INTEGER) — schema validation failed on
-- startup. Widen the column rather than edit the already-applied V34.
-- ============================================================

ALTER TABLE reviews ALTER COLUMN rating TYPE INTEGER;
