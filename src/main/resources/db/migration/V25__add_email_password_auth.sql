-- Email/password is now a second login method alongside phone+OTP, so an
-- account no longer requires a phone number, and needs somewhere to store
-- its password hash.
ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

COMMENT ON COLUMN users.password_hash IS 'BCrypt hash for email/password login; null for phone-only accounts';

-- Case-insensitive uniqueness, mirroring the plain UNIQUE on phone_number
-- (not partial on is_deleted, so a soft-deleted account's email can't be
-- silently reused by a new signup either).
CREATE UNIQUE INDEX idx_users_email_lower_unique ON users (LOWER(email)) WHERE email IS NOT NULL;
