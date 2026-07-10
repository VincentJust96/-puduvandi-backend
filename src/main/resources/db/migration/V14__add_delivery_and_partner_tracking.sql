ALTER TABLE bikes ADD COLUMN latitude DECIMAL(10,8);
ALTER TABLE bikes ADD COLUMN longitude DECIMAL(11,8);

ALTER TABLE bookings ADD COLUMN delivery_type VARCHAR(20) NOT NULL DEFAULT 'SELF_PICKUP';
ALTER TABLE bookings ADD COLUMN dropoff_latitude DECIMAL(10,8);
ALTER TABLE bookings ADD COLUMN dropoff_longitude DECIMAL(11,8);

CREATE TABLE delivery_settings (
    id                  BIGSERIAL PRIMARY KEY,
    rate_per_km         DECIMAL(10,2) NOT NULL,
    updated_by_admin_id BIGINT REFERENCES users(id),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
INSERT INTO delivery_settings (rate_per_km, is_active) VALUES (15.00, true);

CREATE TABLE delivery_orders (
    id                BIGSERIAL PRIMARY KEY,
    booking_id        BIGINT NOT NULL UNIQUE REFERENCES bookings(id),
    partner_id        BIGINT REFERENCES users(id),
    pickup_latitude   DECIMAL(10,8) NOT NULL,
    pickup_longitude  DECIMAL(11,8) NOT NULL,
    dropoff_latitude  DECIMAL(10,8) NOT NULL,
    dropoff_longitude DECIMAL(11,8) NOT NULL,
    distance_km       DECIMAL(8,2) NOT NULL,
    delivery_fee      DECIMAL(10,2) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    claimed_at        TIMESTAMP,
    picked_up_at      TIMESTAMP,
    delivered_at      TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_delivery_orders_status ON delivery_orders(status);
CREATE INDEX idx_delivery_orders_partner ON delivery_orders(partner_id);
