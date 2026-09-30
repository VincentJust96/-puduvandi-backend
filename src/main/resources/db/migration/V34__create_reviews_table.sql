-- ============================================================
-- V34__create_reviews_table.sql
-- Customer star rating + optional comment for a completed booking.
-- One review per booking; bike_id is denormalized onto the review row so
-- a bike's average rating can be computed without joining through bookings.
-- ============================================================

CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT NOT NULL UNIQUE REFERENCES bookings(id),
    bike_id         BIGINT NOT NULL REFERENCES bikes(id),
    customer_id     BIGINT NOT NULL REFERENCES users(id),
    rating          SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         VARCHAR(1000),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

CREATE INDEX idx_reviews_bike ON reviews(bike_id);
