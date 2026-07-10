ALTER TABLE bookings ADD COLUMN current_latitude DECIMAL(10,8);
ALTER TABLE bookings ADD COLUMN current_longitude DECIMAL(11,8);
ALTER TABLE bookings ADD COLUMN location_updated_at TIMESTAMP;
