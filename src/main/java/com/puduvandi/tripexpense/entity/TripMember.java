package com.puduvandi.tripexpense.entity;

import com.puduvandi.auth.entity.User;
import com.puduvandi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One traveler on a {@link Trip}. {@code user} is null until the invited
 * phone number matches (or later registers) a real account — see
 * TripExpenseService.linkPendingMemberships(). displayName is a snapshot
 * taken at add-time (the linked user's full name, or the phone number for
 * a still-pending invite) so the dashboard never shows a blank name.
 */
@Entity
@Table(name = "trip_members")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "phone_number", nullable = false, length = 15)
    private String phoneNumber;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    @Column(name = "is_creator", nullable = false)
    private boolean creator;

    /** How much this traveler has actually handed over toward the trip's shared budget so far — separate from what they've paid for individual expenses. Editable by the trip creator as travelers pay their share. */
    @Builder.Default
    @Column(name = "contributed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal contributedAmount = BigDecimal.ZERO;
}
