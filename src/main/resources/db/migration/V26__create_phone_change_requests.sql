-- Changing an existing login phone number now requires admin approval
-- (OTP against the new number just proves ownership, it no longer applies
-- the change directly) — adding a phone where none exists yet (e.g. an
-- email/password account) is unaffected and stays instant.
CREATE TABLE phone_change_requests (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    old_phone_number    VARCHAR(15),
    new_phone_number    VARCHAR(15) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    remarks             VARCHAR(255),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

CREATE INDEX idx_phone_change_requests_user   ON phone_change_requests(user_id);
CREATE INDEX idx_phone_change_requests_status ON phone_change_requests(status);
