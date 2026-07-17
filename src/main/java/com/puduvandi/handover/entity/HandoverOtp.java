package com.puduvandi.handover.entity;

import com.puduvandi.common.enums.HandoverPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * In-app OTP for a single bike handover step (pickup or return, self or via
 * delivery partner). Append-only audit trail: rows are never deleted, and
 * once created only used/usedAt/verifiedByUserId/failedAttempts ever change.
 * The code itself is never sent via SMS/WhatsApp — it's returned only in the
 * generate API response for in-app display, by design.
 */
@Entity
@Table(name = "handover_otps")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoverOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private HandoverPurpose purpose;

    @Column(name = "otp_code", nullable = false, length = 10)
    private String otpCode;

    @Column(name = "generated_by_user_id", nullable = false)
    private Long generatedByUserId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "verified_by_user_id")
    private Long verifiedByUserId;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isActive(LocalDateTime now) {
        return !used && expiresAt.isAfter(now);
    }
}
