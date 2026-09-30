package com.puduvandi.whatsapp.payment;

import com.puduvandi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A no-login payment link sent to a WhatsApp customer for one booking. */
@Entity
@Table(name = "whatsapp_payment_links")
@Getter
@Setter
@NoArgsConstructor
public class PaymentLink extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** Random, unguessable part of the link — a leaked signing key alone can't forge one. */
    @Column(name = "nonce", nullable = false, unique = true, length = 64)
    private String nonce;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Set when the payment succeeds; after that the link can no longer start a payment. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public PaymentLink(Long bookingId, String nonce, LocalDateTime expiresAt) {
        this.bookingId = bookingId;
        this.nonce = nonce;
        this.expiresAt = expiresAt;
    }
}
