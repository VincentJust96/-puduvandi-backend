package com.puduvandi.bike.entity;

import com.puduvandi.common.entity.BaseEntity;
import com.puduvandi.common.enums.BikeStatus;
import com.puduvandi.common.enums.BikeVerificationStatus;
import com.puduvandi.common.enums.FuelType;
import com.puduvandi.common.enums.TransmissionType;
import com.puduvandi.common.crypto.EncryptedStringConverter;
import com.puduvandi.owner.entity.OwnerProfile;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a bike listed on the Puduvandi marketplace.
 * Owner adds bikes; Admin approves them before they appear to customers.
 */
@Entity
@Table(name = "bikes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private OwnerProfile owner;

    @Column(name = "brand", nullable = false, length = 100)
    private String brand;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "registration_number", nullable = false, unique = true, length = 20)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transmission")
    private TransmissionType transmission;

    @Column(name = "engine_capacity")
    private Integer engineCapacity;

    @Column(name = "helmet_included", nullable = false)
    private boolean helmetIncluded;

    @Column(name = "papers_included", nullable = false)
    @Builder.Default
    private boolean papersIncluded = true;

    @Column(name = "fuel_included", nullable = false)
    @Builder.Default
    private boolean fuelIncluded = true;

    @Column(name = "roadside_assistance", nullable = false)
    @Builder.Default
    private boolean roadsideAssistance = true;

    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    @Column(name = "price_per_day", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerDay;

    @Column(name = "security_deposit", nullable = false, precision = 10, scale = 2)
    private BigDecimal securityDeposit;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BikeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private BikeVerificationStatus verificationStatus;

    @OneToMany(mappedBy = "bike", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<BikeImage> images = new ArrayList<>();

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "area", length = 150)
    private String area;

    @Column(name = "rc_document_url", length = 500)
    private String rcDocumentUrl;

    @Column(name = "insurance_document_url", length = 500)
    private String insuranceDocumentUrl;

    @Column(name = "insurance_policy_number", length = 100)
    private String insurancePolicyNumber;

    @Column(name = "insurance_expiry_date")
    private LocalDate insuranceExpiryDate;

    // Admin/super-admin-only — never included in the customer/owner-facing
    // BikeResponse mapping, see BikeService.toResponse vs AdminService.toBikeResponse.
    // Encrypted at rest — see EncryptedStringConverter for the "v1:" ciphertext
    // format and why read failures return null instead of throwing.
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "insurance_document_password", length = 500)
    private String insuranceDocumentPassword;
}
