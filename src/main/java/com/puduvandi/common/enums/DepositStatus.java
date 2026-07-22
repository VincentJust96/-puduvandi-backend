package com.puduvandi.common.enums;

/**
 * Lifecycle of a booking's security deposit, tracked separately from the
 * booking's own status. REFUNDED covers full, partial, and zero (forfeited)
 * refunds alike — see Booking.depositRefundAmount for the actual amount.
 */
public enum DepositStatus {
    HELD,
    CLAIM_PENDING,
    REFUNDED,
    REFUND_FAILED
}
