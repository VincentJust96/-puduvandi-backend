package com.puduvandi.owner.entity;

import com.puduvandi.auth.entity.User;
import com.puduvandi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Extended profile for users with OWNER role.
 * Contains business info and bank details for commission settlement.
 * Referenced by Bike and Booking entities.
 */
@Entity
@Table(name = "owner_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "business_name", length = 150)
    private String businessName;

    @Column(name = "gstin", length = 20)
    private String gstin;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc_code", length = 20)
    private String bankIfscCode;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_holder_name", length = 100)
    private String accountHolderName;

    @Column(name = "total_bikes", nullable = false)
    private int totalBikes;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
}
