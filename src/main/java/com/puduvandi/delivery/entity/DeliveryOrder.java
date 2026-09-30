package com.puduvandi.delivery.entity;

import com.puduvandi.auth.entity.User;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.common.enums.DeliveryLegType;
import com.puduvandi.common.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One partner-delivery leg for a booking — either OUTBOUND (bike's stored
 * location → customer's chosen drop-off point) or RETURN (customer's
 * location → bike's stored location), charged per km. Each leg is claimed,
 * fulfilled, and paid independently — a booking may have one row per leg
 * type, and each may be completed by a different partner.
 */
@Entity
@Table(name = "delivery_orders")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** Which direction this job runs — a booking has at most one row per leg type. */
    @Enumerated(EnumType.STRING)
    @Column(name = "leg_type", nullable = false)
    private DeliveryLegType legType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private User partner;

    @Column(name = "pickup_latitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", nullable = false, precision = 11, scale = 8)
    private BigDecimal pickupLongitude;

    @Column(name = "dropoff_latitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal dropoffLatitude;

    @Column(name = "dropoff_longitude", nullable = false, precision = 11, scale = 8)
    private BigDecimal dropoffLongitude;

    @Column(name = "distance_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeliveryStatus status;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "partner_current_latitude", precision = 10, scale = 8)
    private BigDecimal partnerCurrentLatitude;

    @Column(name = "partner_current_longitude", precision = 11, scale = 8)
    private BigDecimal partnerCurrentLongitude;

    @Column(name = "partner_location_updated_at")
    private LocalDateTime partnerLocationUpdatedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
