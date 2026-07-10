-- ============================================================
-- V3__create_bike_and_booking_tables.sql
-- Puduvandi - Phase 3: Bike & Booking Schema
-- ============================================================

CREATE TYPE fuel_type AS ENUM ('PETROL', 'ELECTRIC', 'DIESEL');
CREATE TYPE transmission_type AS ENUM ('MANUAL', 'AUTOMATIC');
CREATE TYPE bike_status AS ENUM ('AVAILABLE','RESERVED','UNDER_MAINTENANCE','UNAVAILABLE');
CREATE TYPE bike_verification_status AS ENUM ('PENDING','APPROVED','REJECTED');
CREATE TYPE booking_status AS ENUM (
    'CREATED','PAYMENT_PENDING','CONFIRMED','RESERVED',
    'RIDE_STARTED','RETURN_REQUESTED','COMPLETED','CANCELLED'
);

-- ===== BIKES =====
CREATE TABLE bikes (
    id                      BIGSERIAL PRIMARY KEY,
    owner_id                BIGINT NOT NULL REFERENCES owner_profiles(id),
    brand                   VARCHAR(100) NOT NULL,
    model                   VARCHAR(100) NOT NULL,
    year                    INT NOT NULL,
    registration_number     VARCHAR(20) NOT NULL UNIQUE,
    fuel_type               fuel_type NOT NULL,
    transmission            transmission_type NOT NULL,
    engine_capacity         INT,
    helmet_included         BOOLEAN NOT NULL DEFAULT FALSE,
    price_per_hour          NUMERIC(10,2) NOT NULL,
    price_per_day           NUMERIC(10,2) NOT NULL,
    security_deposit        NUMERIC(10,2) NOT NULL DEFAULT 0,
    description             TEXT,
    status                  bike_status NOT NULL DEFAULT 'UNAVAILABLE',
    verification_status     bike_verification_status NOT NULL DEFAULT 'PENDING',
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100)
);

CREATE INDEX idx_bikes_owner_id            ON bikes(owner_id);
CREATE INDEX idx_bikes_status              ON bikes(status);
CREATE INDEX idx_bikes_verification_status ON bikes(verification_status);
CREATE INDEX idx_bikes_fuel_type           ON bikes(fuel_type);
CREATE INDEX idx_bikes_deleted             ON bikes(is_deleted);

-- ===== BIKE IMAGES =====
CREATE TABLE bike_images (
    id          BIGSERIAL PRIMARY KEY,
    bike_id     BIGINT NOT NULL REFERENCES bikes(id) ON DELETE CASCADE,
    image_url   VARCHAR(500) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bike_images_bike_id ON bike_images(bike_id);

-- ===== BOOKINGS =====
CREATE TABLE bookings (
    id                      BIGSERIAL PRIMARY KEY,
    booking_reference       VARCHAR(20) NOT NULL UNIQUE,
    customer_id             BIGINT NOT NULL REFERENCES users(id),
    bike_id                 BIGINT NOT NULL REFERENCES bikes(id),
    owner_id                BIGINT NOT NULL REFERENCES owner_profiles(id),
    pickup_datetime         TIMESTAMP NOT NULL,
    return_datetime         TIMESTAMP NOT NULL,
    actual_return_datetime  TIMESTAMP,
    total_hours             NUMERIC(10,2) NOT NULL,
    total_days              NUMERIC(10,2) NOT NULL,
    base_amount             NUMERIC(10,2) NOT NULL,
    security_deposit        NUMERIC(10,2) NOT NULL DEFAULT 0,
    total_amount            NUMERIC(10,2) NOT NULL,
    commission_percent      NUMERIC(5,2) NOT NULL,
    commission_amount       NUMERIC(10,2) NOT NULL,
    owner_earning           NUMERIC(10,2) NOT NULL,
    status                  booking_status NOT NULL DEFAULT 'CREATED',
    cancellation_reason     VARCHAR(500),
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100)
);

CREATE INDEX idx_bookings_customer_id     ON bookings(customer_id);
CREATE INDEX idx_bookings_bike_id         ON bookings(bike_id);
CREATE INDEX idx_bookings_owner_id        ON bookings(owner_id);
CREATE INDEX idx_bookings_status          ON bookings(status);
CREATE INDEX idx_bookings_reference       ON bookings(booking_reference);
CREATE INDEX idx_bookings_pickup_datetime ON bookings(pickup_datetime);

CREATE TRIGGER trg_bikes_updated_at
    BEFORE UPDATE ON bikes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_bookings_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
