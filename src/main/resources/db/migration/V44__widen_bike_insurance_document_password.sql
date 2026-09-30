-- ============================================================
-- V44__widen_bike_insurance_document_password.sql
-- insurance_document_password is now encrypted at rest (AES/GCM, see
-- EncryptedStringConverter) — ciphertext (IV + tag + base64 + "v1:" prefix)
-- is longer than the original plaintext, so VARCHAR(255) is no longer enough
-- for longer passwords.
-- ============================================================

ALTER TABLE bikes ALTER COLUMN insurance_document_password TYPE VARCHAR(500);
