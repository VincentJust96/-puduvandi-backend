-- ============================================================
-- V28__seed_super_admin.sql
-- Bootstraps the single SUPER_ADMIN account. Logs in through the
-- normal OTP flow like any other user — no special-cased auth code.
-- Idempotent: re-running promotes whatever account already holds
-- this phone number rather than failing on the unique constraint.
-- ============================================================

INSERT INTO users (phone_number, full_name, role, status, kyc_status, is_deleted)
VALUES ('9090909090', 'Super Admin', 'SUPER_ADMIN', 'ACTIVE', 'NOT_SUBMITTED', FALSE)
ON CONFLICT (phone_number) DO UPDATE SET role = 'SUPER_ADMIN';
