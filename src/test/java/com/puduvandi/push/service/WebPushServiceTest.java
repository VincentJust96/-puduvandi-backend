package com.puduvandi.push.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.config.PushProperties;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.push.dto.SubscribeRequest;
import com.puduvandi.push.entity.PushSubscription;
import com.puduvandi.push.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Real generated VAPID keys are used here (same ones as application.yml's dev
 * default) so PushService can actually be constructed — sends still target a
 * fake endpoint, so no real network delivery happens; these tests exercise the
 * "never throws past the caller" contract, not real push delivery.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebPushService Unit Tests")
class WebPushServiceTest {

    @Mock private PushSubscriptionRepository pushSubscriptionRepository;
    @Mock private UserRepository userRepository;

    private WebPushService webPushService;

    private static final Long USER_ID = 5L;

    @BeforeEach
    void setUp() {
        PushProperties props = new PushProperties();
        props.setVapidPublicKey("BKjTGkFD_SyqnXf_IhH2Wwf5WbEbYtEFehXyd5PQVkzAERuCDyBNjxv74_MvF0EENcjDMtB9A9L-0DGTn43vLqE");
        props.setVapidPrivateKey("Lar7gckcdrMYnkoEQtrN8q0chiLiW6OcUPexTtpW4cc");
        props.setSubject("mailto:support@puduvandi.com");
        webPushService = new WebPushService(pushSubscriptionRepository, userRepository, props);
    }

    private SubscribeRequest subscribeRequest(String endpoint) {
        return new SubscribeRequest(endpoint, new SubscribeRequest.Keys("fake-p256dh", "fake-auth"));
    }

    // ===== subscribe =====

    @Test
    @DisplayName("subscribe: unknown user throws ResourceNotFoundException")
    void subscribe_unknownUser_throws() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webPushService.subscribe(USER_ID, subscribeRequest("https://push.example/ep1"), "TestAgent"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(pushSubscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("subscribe: new endpoint creates a fresh subscription")
    void subscribe_newEndpoint_createsSubscription() {
        User user = User.builder().id(USER_ID).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(pushSubscriptionRepository.findByEndpoint("https://push.example/ep1")).thenReturn(Optional.empty());
        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);

        webPushService.subscribe(USER_ID, subscribeRequest("https://push.example/ep1"), "TestAgent");

        verify(pushSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getEndpoint()).isEqualTo("https://push.example/ep1");
        assertThat(captor.getValue().getP256dhKey()).isEqualTo("fake-p256dh");
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("subscribe: existing endpoint upserts in place rather than duplicating")
    void subscribe_existingEndpoint_updatesInPlace() {
        User user = User.builder().id(USER_ID).build();
        PushSubscription existing = PushSubscription.builder().id(9L).endpoint("https://push.example/ep1").build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(pushSubscriptionRepository.findByEndpoint("https://push.example/ep1")).thenReturn(Optional.of(existing));
        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);

        webPushService.subscribe(USER_ID, subscribeRequest("https://push.example/ep1"), "TestAgent");

        verify(pushSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9L);
    }

    // ===== unsubscribe =====

    @Test
    @DisplayName("unsubscribe: deletes by endpoint")
    void unsubscribe_deletesByEndpoint() {
        webPushService.unsubscribe("https://push.example/ep1");

        verify(pushSubscriptionRepository).deleteByEndpoint("https://push.example/ep1");
    }

    // ===== sendToUsers / sendToUser =====

    @Test
    @DisplayName("sendToUsers: empty user list is a no-op, no repository call")
    void sendToUsers_emptyUserIds_noop() {
        webPushService.sendToUsers(List.of(), "Title", "Body", "/x");

        verifyNoInteractions(pushSubscriptionRepository);
    }

    @Test
    @DisplayName("sendToUsers: no subscriptions found is a no-op")
    void sendToUsers_noSubscriptionsFound_noop() {
        when(pushSubscriptionRepository.findByUser_IdIn(List.of(USER_ID))).thenReturn(List.of());

        assertThatCode(() -> webPushService.sendToUsers(List.of(USER_ID), "Title", "Body", "/x"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendToUser: a malformed/fake subscription never throws past the caller")
    void sendToUser_malformedSubscription_doesNotThrow() {
        PushSubscription sub = PushSubscription.builder()
                .id(1L).endpoint("https://push.example/ep1")
                .p256dhKey("not-a-real-key").authKey("not-a-real-key")
                .build();
        when(pushSubscriptionRepository.findByUser_IdIn(List.of(USER_ID))).thenReturn(List.of(sub));

        assertThatCode(() -> webPushService.sendToUser(USER_ID, "Title", "Body", "/x"))
                .doesNotThrowAnyException();
    }
}
