ALTER TABLE delivery_orders ADD COLUMN leg_type VARCHAR(20) NOT NULL DEFAULT 'OUTBOUND';

ALTER TABLE delivery_orders DROP CONSTRAINT delivery_orders_booking_id_key;
ALTER TABLE delivery_orders ADD CONSTRAINT uq_delivery_orders_booking_leg UNIQUE (booking_id, leg_type);

ALTER TABLE delivery_orders DROP COLUMN return_collected_at;
ALTER TABLE delivery_orders DROP COLUMN return_completed_at;
