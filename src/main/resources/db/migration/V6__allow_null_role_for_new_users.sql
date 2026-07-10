-- Allow role to be NULL temporarily for new users who haven't selected their role yet.
-- Role is set via POST /api/v1/auth/set-role after OTP verification.
ALTER TABLE users ALTER COLUMN role DROP NOT NULL;
