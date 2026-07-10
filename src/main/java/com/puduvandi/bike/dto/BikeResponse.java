package com.puduvandi.bike.dto;

import com.puduvandi.common.enums.BikeStatus;
import com.puduvandi.common.enums.BikeVerificationStatus;
import com.puduvandi.common.enums.FuelType;
import com.puduvandi.common.enums.TransmissionType;

import java.math.BigDecimal;
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
    BigDecimal pricePerHour,
    BigDecimal pricePerDay,
    BigDecimal securityDeposit,
    String description,
    BikeStatus status,
    BikeVerificationStatus verificationStatus,
    List<String> imageUrls,
    LocalDateTime createdAt,
    BigDecimal latitude,
    BigDecimal longitude
) {}
