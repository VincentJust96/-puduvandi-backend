package com.puduvandi.common.enums;

public enum DeliveryStatus {
    PENDING,
    CLAIMED,
    PICKED_UP,
    DELIVERED,
    /** Customer has handed the bike back to the partner (return leg). */
    RETURN_COLLECTED,
    /** Partner has handed the returned bike back to the owner — return complete. */
    RETURN_COMPLETED,
    CANCELLED
}
