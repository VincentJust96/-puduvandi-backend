-- ============================================================
-- V41__create_bike_condition_reviews.sql
-- Customer-submitted bike condition snapshot (photos, fuel level,
-- odometer reading) taken right before pickup — one per booking.
-- Gives the owner a timestamped baseline to check against if they
-- later raise a damage/deposit claim. See BikeConditionReviewService
-- and HandoverOtpService (generate() requires this to exist before a
-- pickup OTP can be issued).
-- ============================================================

CREATE TABLE bike_condition_reviews (
    id            BIGSERIAL PRIMARY KEY,
    booking_id    BIGINT NOT NULL REFERENCES bookings(id),
    customer_id   BIGINT NOT NULL REFERENCES users(id),
    fuel_level    VARCHAR(20) NOT NULL,
    odometer_km   INT NOT NULL,
    photo_urls    VARCHAR(2000) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now(),
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100)
);

CREATE UNIQUE INDEX idx_bike_condition_reviews_booking ON bike_condition_reviews(booking_id);
