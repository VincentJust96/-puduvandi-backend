package com.puduvandi.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores OTP codes for phone-based authentication.
 * Each OTP is single-use and expires after a configured duration.
 */
@Entity
@Table(name = "otp_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 15)
    private String phoneNumber;

    @Column(name = "otp_code", nullable = false, length = 10)
    private String otpCode;

    @Column(name = "purpose", nullable = false, length = 50)
    private String purpose;

    @Column(name = "is_used", nullable = false)
    private boolean used;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
