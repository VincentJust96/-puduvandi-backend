package com.puduvandi.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Typed publish helpers wired into existing services at their existing
 * mutation points — never changes business logic, just notifies whoever's
 * watching that something changed so the frontend can silently re-fetch
 * instead of requiring a manual refresh. Every method swallows its own
 * failures: a push notification going out must never break the underlying
 * business transaction (same rule ErrorLogService/WebPushService follow).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeEventPublisher {

    private final SseConnectionRegistry registry;

    public void bookingUpdated(Long bookingId, String status, Long customerId, Long ownerUserId, Long partnerUserId) {
        safe(() -> {
            Map<String, Object> payload = Map.of("bookingId", bookingId, "status", status);
            RealtimeEvent event = RealtimeEvent.of(RealtimeEventType.BOOKING_UPDATED, payload);
            registry.sendToUser(customerId, event);
            registry.sendToUser(ownerUserId, event);
            if (partnerUserId != null) {
                registry.sendToUser(partnerUserId, event);
            }
            adminBroadcast(RealtimeEventType.ADMIN_ALERT, "booking", payload);
        });
    }

    public void kycUpdated(Long userId, String kycStatus) {
        safe(() -> {
            RealtimeEvent event = RealtimeEvent.of(RealtimeEventType.KYC_UPDATED,
                    Map.of("userId", userId, "kycStatus", kycStatus));
            registry.sendToUser(userId, event);
            adminBroadcast(RealtimeEventType.KYC_UPDATED, "kyc", event.payload());
        });
    }

    public void bikeUpdated(Long bikeId, Long ownerUserId, String status) {
        safe(() -> {
            RealtimeEvent event = RealtimeEvent.of(RealtimeEventType.BIKE_UPDATED,
                    Map.of("bikeId", bikeId, "status", status));
            registry.sendToUser(ownerUserId, event);
        });
    }

    public void depositClaimUpdated(Long bookingId, Long customerId, Long ownerUserId, String depositStatus) {
        safe(() -> {
            Map<String, Object> payload = Map.of("bookingId", bookingId, "depositStatus", depositStatus);
            RealtimeEvent event = RealtimeEvent.of(RealtimeEventType.DEPOSIT_CLAIM_UPDATED, payload);
            registry.sendToUser(customerId, event);
            registry.sendToUser(ownerUserId, event);
            adminBroadcast(RealtimeEventType.DEPOSIT_CLAIM_UPDATED, "deposit-claim", payload);
        });
    }

    /** New delivery job became claimable — broadcast to every connected partner. */
    public void deliveryJobAvailable() {
        safe(() -> registry.sendToRole("PARTNER",
                RealtimeEvent.of(RealtimeEventType.DELIVERY_UPDATED, Map.of("reason", "NEW_JOB"))));
    }

    public void deliveryUpdated(Long deliveryId, String status, Long customerId, Long ownerUserId, Long partnerUserId) {
        safe(() -> {
            RealtimeEvent event = RealtimeEvent.of(RealtimeEventType.DELIVERY_UPDATED,
                    Map.of("deliveryId", deliveryId, "status", status));
            registry.sendToUser(customerId, event);
            registry.sendToUser(ownerUserId, event);
            if (partnerUserId != null) {
                registry.sendToUser(partnerUserId, event);
            }
        });
    }

    public void locationUpdated(Long bookingId, Collection<Long> watcherUserIds) {
        safe(() -> registry.sendToUsers(watcherUserIds,
                RealtimeEvent.of(RealtimeEventType.LOCATION_UPDATED, Map.of("bookingId", bookingId))));
    }

    /** category: "user" | "kyc" | "bike" | "booking" | "error-log" | "deposit-claim" | "phone-change" */
    public void adminAlert(String category, Map<String, Object> payload) {
        safe(() -> adminBroadcast(RealtimeEventType.ADMIN_ALERT, category, payload));
    }

    private void adminBroadcast(RealtimeEventType type, String category, Map<String, Object> payload) {
        RealtimeEvent event = RealtimeEvent.of(type, category, new HashMap<>(payload));
        registry.sendToRole("ADMIN", event);
        registry.sendToRole("SUPER_ADMIN", event);
    }

    private void safe(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("Realtime event publish failed (non-fatal): {}", ex.getMessage());
        }
    }
}
