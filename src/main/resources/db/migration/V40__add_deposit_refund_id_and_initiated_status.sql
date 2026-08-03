-- ============================================================
-- V40__add_deposit_refund_id_and_initiated_status.sql
-- Introduces the REFUND_INITIATED deposit_status (a real Razorpay refund
-- request was accepted but not yet confirmed settled) and a column to
-- track the Razorpay refund id, so an incoming refund.processed /
-- refund.failed webhook event can be matched back to the booking that
-- requested it. See PaymentService.refundDeposit / handleRefundWebhookEvent
-- and RazorpayWebhookController.
-- ============================================================

ALTER TABLE bookings
    ADD COLUMN deposit_razorpay_refund_id VARCHAR(100);

CREATE INDEX idx_bookings_deposit_razorpay_refund_id ON bookings(deposit_razorpay_refund_id);
