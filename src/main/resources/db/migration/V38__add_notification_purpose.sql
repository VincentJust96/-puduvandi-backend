-- Which booking-lifecycle message this is (BOOKING_CONFIRMATION, PICKUP_REMINDER,
-- RIDE_COMPLETION, OTP, ADHOC). Needed so the scheduled retry sweep knows which
-- MSG91 DLT template to retry with, since each purpose maps to a separate
-- DLT-approved template — see NotificationPurpose.
ALTER TABLE notification_logs
    ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'ADHOC';
