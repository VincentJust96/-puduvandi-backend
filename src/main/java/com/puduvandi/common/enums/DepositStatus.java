package com.puduvandi.common.enums;

/**
 * Lifecycle of a booking's security deposit, tracked separately from the
 * booking's own status. REFUNDED covers full, partial, and zero (forfeited)
 * refunds alike — see Booking.depositRefundAmount for the actual amount.
 * <p>
 * REFUND_INITIATED sits between HELD and REFUNDED for a real (non-mock)
 * Razorpay refund: it means the refund request was accepted by Razorpay but
 * not yet confirmed settled — that confirmation only arrives later via the
 * refund.processed webhook (see PaymentService.handleRefundWebhookEvent).
 * Mock-mode and zero-amount refunds have no external confirmation to wait
 * for, so they go straight to REFUNDED.
 */
public enum DepositStatus {
    HELD,
    CLAIM_PENDING,
    REFUND_INITIATED,
    REFUNDED,
    REFUND_FAILED
}
