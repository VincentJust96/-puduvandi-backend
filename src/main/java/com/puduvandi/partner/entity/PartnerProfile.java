package com.puduvandi.partner.entity;

import com.puduvandi.auth.entity.User;
import com.puduvandi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Extended profile for users with PARTNER (delivery partner) role.
 * KYC approval is gated via the shared User.kycStatus field, same
 * mechanism as OwnerProfile — see PartnerProfileService.uploadDocument().
 */
@Entity
@Table(name = "partner_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "total_deliveries", nullable = false)
    private int totalDeliveries;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
}
