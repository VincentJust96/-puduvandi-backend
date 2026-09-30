package com.puduvandi.realtime;

import java.time.Instant;
import java.util.Map;

/**
 * A single push event delivered over SSE. {@code category} is only used by
 * ADMIN_ALERT so the frontend's admin tabs can filter to the ones relevant to
 * whichever tab is currently open (e.g. "user", "kyc", "bike", "booking",
 * "error-log", "deposit-claim").
 */
public record RealtimeEvent(
        RealtimeEventType type,
        String category,
        Map<String, Object> payload,
        Instant timestamp
) {
    public static RealtimeEvent of(RealtimeEventType type, Map<String, Object> payload) {
        return new RealtimeEvent(type, null, payload, Instant.now());
    }

    public static RealtimeEvent of(RealtimeEventType type, String category, Map<String, Object> payload) {
        return new RealtimeEvent(type, category, payload, Instant.now());
    }
}
