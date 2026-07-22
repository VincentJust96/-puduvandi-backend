package com.puduvandi.push.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.config.PushProperties;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.push.dto.SubscribeRequest;
import com.puduvandi.push.entity.PushSubscription;
import com.puduvandi.push.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.List;

/**
 * Browser Web Push (VAPID) — replaces the previously poll-only real-time
 * updates for a curated set of high-value events (see DeliveryService,
 * DepositClaimService, BookingService for the actual trigger points).
 * <p>
 * Never throws past the caller — a push failure (bad keys, network, expired
 * subscription) must never break the booking/delivery/deposit flow that
 * triggered it, same "never breaks the calling flow" convention as
 * NotificationService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {

    static {
        // The web-push library requests the "BC" security provider by name for
        // its EC/VAPID crypto but never registers it itself — confirmed live:
        // every send failed with NoSuchProviderException until this was added.
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;
    private final PushProperties pushProperties;

    @Transactional
    public void subscribe(Long userId, SubscribeRequest request, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);
        subscription.setUser(user);
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dhKey(request.keys().p256dh());
        subscription.setAuthKey(request.keys().auth());
        subscription.setUserAgent(userAgent);
        pushSubscriptionRepository.save(subscription);

        log.info("Push subscription saved: userId={}", userId);
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        pushSubscriptionRepository.deleteByEndpoint(endpoint);
    }

    public void sendToUser(Long userId, String title, String body, String url) {
        sendToUsers(List.of(userId), title, body, url);
    }

    @Transactional
    public void sendToUsers(List<Long> userIds, String title, String body, String url) {
        if (userIds == null || userIds.isEmpty()) return;

        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUser_IdIn(userIds);
        if (subscriptions.isEmpty()) return;

        PushService pushService;
        try {
            pushService = new PushService(pushProperties.getVapidPublicKey(),
                    pushProperties.getVapidPrivateKey(), pushProperties.getSubject());
        } catch (Exception ex) {
            log.error("Could not build PushService — check puduvandi.push.* config", ex);
            return;
        }

        String payload = new JSONObject()
                .put("title", title)
                .put("body", body)
                .put("url", url == null ? "/" : url)
                .toString();

        for (PushSubscription sub : subscriptions) {
            try {
                Subscription.Keys keys = new Subscription.Keys(sub.getP256dhKey(), sub.getAuthKey());
                Subscription subscription = new Subscription(sub.getEndpoint(), keys);
                Notification notification = new Notification(subscription, payload);
                HttpResponse response = pushService.send(notification, Encoding.AES128GCM);

                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    // Expired/revoked on the browser's end — standard Web Push cleanup.
                    pushSubscriptionRepository.deleteByEndpoint(sub.getEndpoint());
                    log.info("Removed expired push subscription: id={}", sub.getId());
                } else if (status >= 300) {
                    log.warn("Push send returned {} for subscription id={}", status, sub.getId());
                }
            } catch (Exception ex) {
                log.warn("Push send failed for subscription id={}: {}", sub.getId(), ex.getMessage());
            }
        }
    }
}
