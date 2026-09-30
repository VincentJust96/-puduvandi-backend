ALTER TABLE trips ADD COLUMN invite_token VARCHAR(32);

UPDATE trips SET invite_token = replace(gen_random_uuid()::text, '-', '') WHERE invite_token IS NULL;

ALTER TABLE trips ALTER COLUMN invite_token SET NOT NULL;
ALTER TABLE trips ADD CONSTRAINT uq_trips_invite_token UNIQUE (invite_token);
