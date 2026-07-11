-- ============================================================
-- V16__make_notification_logs_booking_id_nullable.sql
-- OTP SMS sends go through notification_logs too but have no booking
-- to attach to, so booking_id can no longer be mandatory.
-- ============================================================

ALTER TABLE notification_logs ALTER COLUMN booking_id DROP NOT NULL;
