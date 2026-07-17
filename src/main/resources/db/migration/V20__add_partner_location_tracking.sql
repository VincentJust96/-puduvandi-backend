-- ============================================================
-- V20__add_partner_location_tracking.sql
-- Adds a live location slot for the delivery partner, mirroring
-- the existing customer-location columns on bookings (V13).
-- Tracking target (partner vs customer) switches per handover
-- leg — resolved at read time by TrackingService, not stored.
-- ============================================================

ALTER TABLE delivery_orders ADD COLUMN partner_current_latitude  DECIMAL(10,8);
ALTER TABLE delivery_orders ADD COLUMN partner_current_longitude DECIMAL(11,8);
ALTER TABLE delivery_orders ADD COLUMN partner_location_updated_at TIMESTAMP;
