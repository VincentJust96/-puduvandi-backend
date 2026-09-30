package com.puduvandi.push.entity;

import com.puduvandi.auth.entity.User;
import com.puduvandi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * One browser/device push subscription for a user (a user may have several —
 * one per device/browser they've enabled notifications on).
 */
@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "endpoint", nullable = false, unique = true, length = 500)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false)
    private String authKey;

    @Column(name = "user_agent")
    private String userAgent;
}
