package com.puduvandi.notification.entity;

import com.puduvandi.common.entity.BaseEntity;
import com.puduvandi.common.enums.NotificationStatus;
import com.puduvandi.common.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit trail for every SMS/WhatsApp message the platform attempts to send.
 * One row per send attempt per channel (sendBoth() writes two rows).
 */
@Entity
@Table(name = "notification_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for non-booking sends (e.g. login OTP) */
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "message_content", columnDefinition = "TEXT")
    private String messageContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 20)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    /** Provider message ID once accepted for delivery (provider-agnostic; was twilio_sid) */
    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;
}
