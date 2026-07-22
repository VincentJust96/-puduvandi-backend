-- ============================================================
-- V32__create_push_subscriptions.sql
-- Browser Web Push (VAPID) subscriptions. One row per device/browser a
-- user has enabled notifications on — a user can have several.
-- ============================================================

CREATE TABLE push_subscriptions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    endpoint        VARCHAR(500) NOT NULL UNIQUE,
    p256dh_key      VARCHAR(255) NOT NULL,
    auth_key        VARCHAR(255) NOT NULL,
    user_agent      VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions(user_id);
