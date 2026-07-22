package com.puduvandi.common.enums;

/**
 * Which direction a partner-delivery job runs. Both legs of a booking are
 * independently claimable jobs, each with its own PENDING → CLAIMED →
 * PICKED_UP → DELIVERED lifecycle and its own fee — a different partner may
 * complete each leg.
 */
public enum DeliveryLegType {
    /** Owner's bike → customer's chosen drop-off point. */
    OUTBOUND,
    /** Customer's location → owner's bike base. Created once the customer requests a return. */
    RETURN
}
