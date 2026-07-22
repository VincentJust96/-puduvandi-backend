-- V29 collapsed the return-leg-specific statuses (RETURN_COLLECTED, RETURN_COMPLETED)
-- into the same PICKED_UP/DELIVERED values used for the outbound leg, now that leg_type
-- distinguishes direction. Rows written before that refactor still hold the old strings,
-- which no longer match the DeliveryStatus enum and crash deserialization on read.
UPDATE delivery_orders SET status = 'PICKED_UP' WHERE status = 'RETURN_COLLECTED';
UPDATE delivery_orders SET status = 'DELIVERED' WHERE status = 'RETURN_COMPLETED';
