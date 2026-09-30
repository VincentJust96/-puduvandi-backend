package com.puduvandi.realtime;

/**
 * Kinds of push events delivered over the SSE stream (see RealtimeController).
 * The frontend subscribes by event name and re-fetches its own data on receipt
 * rather than trying to apply the payload as a state patch — see
 * RealtimeEventPublisher for what each type's payload actually contains.
 */
public enum RealtimeEventType {
    BOOKING_UPDATED,
    BIKE_UPDATED,
    KYC_UPDATED,
    DEPOSIT_CLAIM_UPDATED,
    DELIVERY_UPDATED,
    ADMIN_ALERT,
    LOCATION_UPDATED
}
