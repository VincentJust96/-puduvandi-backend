package com.puduvandi.payment.entity;

import com.puduvandi.common.entity.BaseEntity;
import com.puduvandi.common.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One row per payment attempt. May cover multiple bookings created in the
 * same checkout trip (see Booking.payment). A booking's payment_id points
 * at its most recent attempt — retrying after a failed/expired attempt
 * creates a new Payment row rather than reusing the old one.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    /** True for bookings auto-confirmed via PAYMENT_MOCK_ENABLED (no real gateway involved). */
    @Column(name = "mock", nullable = false)
    @Builder.Default
    private boolean mock = false;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
