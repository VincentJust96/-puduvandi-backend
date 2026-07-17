-- ============================================================
-- V22__create_payments_table.sql
-- Razorpay payment records. One row per payment attempt, may cover
-- multiple bookings created in the same checkout trip.
-- ============================================================

CREATE TABLE payments (
    id                     BIGSERIAL PRIMARY KEY,
    customer_id            BIGINT NOT NULL,
    amount                 NUMERIC(10,2) NOT NULL,
    currency               VARCHAR(10) NOT NULL DEFAULT 'INR',
    status                 VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    razorpay_order_id      VARCHAR(100),
    razorpay_payment_id    VARCHAR(100),
    razorpay_signature     VARCHAR(255),
    mock                   BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason         VARCHAR(500),
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by             VARCHAR(100),
    updated_by             VARCHAR(100)
);

CREATE INDEX idx_payments_customer_id       ON payments(customer_id);
CREATE INDEX idx_payments_razorpay_order_id ON payments(razorpay_order_id);
CREATE INDEX idx_payments_status            ON payments(status);

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE bookings ADD COLUMN payment_id BIGINT REFERENCES payments(id);
CREATE INDEX idx_bookings_payment_id ON bookings(payment_id);
