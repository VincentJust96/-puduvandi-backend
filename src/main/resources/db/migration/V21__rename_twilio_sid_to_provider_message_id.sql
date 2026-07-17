-- ============================================================
-- V21__rename_twilio_sid_to_provider_message_id.sql
-- Twilio was removed as the SMS/WhatsApp provider; this column is now
-- provider-agnostic so whichever replacement is chosen doesn't need
-- another rename.
-- ============================================================

ALTER TABLE notification_logs RENAME COLUMN twilio_sid TO provider_message_id;
