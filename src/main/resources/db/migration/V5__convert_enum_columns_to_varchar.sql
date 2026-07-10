-- ============================================================
-- V5__convert_enum_columns_to_varchar.sql
-- Convert all PostgreSQL custom enum type columns to VARCHAR.
-- Hibernate binds null enum parameters as 'character varying',
-- which has no = operator against custom enum types in PostgreSQL.
-- VARCHAR columns work correctly with null params in JPQL queries.
-- ============================================================

-- ===== V1 enum columns: users =====

ALTER TABLE users
    ALTER COLUMN role       TYPE VARCHAR(50)  USING role::text,
    ALTER COLUMN status     TYPE VARCHAR(50)  USING status::text,
    ALTER COLUMN kyc_status TYPE VARCHAR(50)  USING kyc_status::text;

-- ===== V2 enum columns: user_documents, owner_documents =====

ALTER TABLE user_documents
    ALTER COLUMN document_type TYPE VARCHAR(50) USING document_type::text,
    ALTER COLUMN status        TYPE VARCHAR(50) USING status::text;

ALTER TABLE owner_documents
    ALTER COLUMN document_type TYPE VARCHAR(50) USING document_type::text,
    ALTER COLUMN status        TYPE VARCHAR(50) USING status::text;

-- ===== V3 enum columns: bikes, bookings =====

ALTER TABLE bikes
    ALTER COLUMN fuel_type            TYPE VARCHAR(50) USING fuel_type::text,
    ALTER COLUMN transmission         TYPE VARCHAR(50) USING transmission::text,
    ALTER COLUMN status               TYPE VARCHAR(50) USING status::text,
    ALTER COLUMN verification_status  TYPE VARCHAR(50) USING verification_status::text;

ALTER TABLE bookings
    ALTER COLUMN status TYPE VARCHAR(50) USING status::text;

-- ===== Drop now-unused custom enum types =====
-- CASCADE drops dependent column defaults that still reference the old enum type.

DROP TYPE IF EXISTS user_role CASCADE;
DROP TYPE IF EXISTS user_status CASCADE;
DROP TYPE IF EXISTS kyc_status CASCADE;
DROP TYPE IF EXISTS document_type CASCADE;
DROP TYPE IF EXISTS document_status CASCADE;
DROP TYPE IF EXISTS fuel_type CASCADE;
DROP TYPE IF EXISTS transmission_type CASCADE;
DROP TYPE IF EXISTS bike_status CASCADE;
DROP TYPE IF EXISTS bike_verification_status CASCADE;
DROP TYPE IF EXISTS booking_status CASCADE;
