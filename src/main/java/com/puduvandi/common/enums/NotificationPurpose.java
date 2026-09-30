package com.puduvandi.common.enums;

/**
 * Which booking-lifecycle message this is. Needed because each one maps to a
 * separate DLT-approved MSG91 template — TRAI requires the exact registered
 * template per message, not one generic "send anything" template.
 */
public enum NotificationPurpose {
    BOOKING_CONFIRMATION,
    PICKUP_REMINDER,
    RIDE_COMPLETION,
    OTP,
    /** Free-text admin one-off send. Will fail against MSG91 until it's pointed
     *  at a template whose registered content matches the message verbatim —
     *  DLT doesn't allow arbitrary text, so this only works for messages that
     *  happen to match an already-approved template. */
    ADHOC
}
