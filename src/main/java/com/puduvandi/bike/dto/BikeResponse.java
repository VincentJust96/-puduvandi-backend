package com.puduvandi.bike.dto;

import com.puduvandi.common.enums.BikeStatus;
import com.puduvandi.common.enums.BikeVerificationStatus;
import com.puduvandi.common.enums.FuelType;
import com.puduvandi.common.enums.TransmissionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BikeResponse(
    Long id,
    Long ownerId,
    String ownerName,
    String brand,
    String model,
    Integer year,
    String registrationNumber,
    FuelType fuelType,
    TransmissionType transmission,
    Integer engineCapacity,
    boolean helmetIncluded,
    boolean papersIncluded,
    boolean fuelIncluded,
    boolean roadsideAssistance,
    BigDecimal pricePerHour,
    BigDecimal pricePerDay,
    BigDecimal securityDeposit,
    String description,
    BikeStatus status,
    BikeVerificationStatus verificationStatus,
    List<String> imageUrls,
    LocalDateTime createdAt,
    BigDecimal latitude,
    BigDecimal longitude,
    String area,
    long totalTrips,
    /** Average of all reviews for this bike, null when it has none yet. */
    Double rating,
    String rcDocumentUrl,
    String insuranceDocumentUrl,
    String insurancePolicyNumber,
    LocalDate insuranceExpiryDate,
    /**
     * The insurance PDF's password, if it was encrypted — admin/super-admin only.
     * BikeService.toResponse (customer browse/detail, owner add/update/my-bikes)
     * always passes null here; only AdminService.toBikeResponse (admin bike list/
     * approve/reject/update) fills in the real value.
     */
    String insuranceDocumentPassword
) {}
