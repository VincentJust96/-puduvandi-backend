package com.puduvandi.tripexpense.entity;

import com.puduvandi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A trip whose expenses are tracked and split across its members (see
 * {@link TripMember}). Independent of the bike-rental role system —
 * any authenticated user can create or join one.
 */
@Entity
@Table(name = "trips")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "budget", nullable = false, precision = 12, scale = 2)
    private BigDecimal budget;

    /** Optional uploaded background image for the dashboard hero banner. */
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    /** Vertical focal point of the cover image, 0-100 (50 = center) — lets a photo be repositioned instead of always center-cropped. */
    @Builder.Default
    @Column(name = "cover_image_position_y", nullable = false)
    private Integer coverImagePositionY = 50;

    /** Opaque, unguessable token for the shareable "join this trip" link — any member can hand it out to bring in more travelers. */
    @Column(name = "invite_token", nullable = false, unique = true, length = 32)
    private String inviteToken;
}
