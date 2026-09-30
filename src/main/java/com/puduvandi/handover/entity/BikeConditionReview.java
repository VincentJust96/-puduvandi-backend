package com.puduvandi.handover.entity;

import com.puduvandi.auth.entity.User;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.common.entity.BaseEntity;
import com.puduvandi.common.enums.FuelLevel;
import jakarta.persistence.*;
import lombok.*;

/**
 * A customer's pre-pickup snapshot of the bike's condition — photos, fuel
 * level, odometer reading — filed once per booking right before they
 * generate their pickup OTP. Gives the owner a timestamped baseline to
 * compare against if damage is disputed later. See BikeConditionReviewService.
 */
@Entity
@Table(name = "bike_condition_reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BikeConditionReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_level", nullable = false)
    private FuelLevel fuelLevel;

    @Column(name = "odometer_km", nullable = false)
    private Integer odometerKm;

    /** Comma-joined photo URLs — same list<->string mapping as DepositClaim.photoUrls. */
    @Column(name = "photo_urls", nullable = false, length = 2000)
    private String photoUrls;
}
