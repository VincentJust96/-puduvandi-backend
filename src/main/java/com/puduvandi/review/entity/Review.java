package com.puduvandi.review.entity;

import com.puduvandi.auth.entity.User;
import com.puduvandi.bike.entity.Bike;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A customer's star rating (+ optional comment) for one completed booking.
 * One review per booking; bike is denormalized here so a bike's average
 * rating can be computed without joining through bookings.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bike_id", nullable = false)
    private Bike bike;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", length = 1000)
    private String comment;
}
