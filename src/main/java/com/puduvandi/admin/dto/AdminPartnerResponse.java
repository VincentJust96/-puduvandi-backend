package com.puduvandi.admin.dto;

import com.puduvandi.common.enums.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Delivery partner summary for admin KYC review")
public record AdminPartnerResponse(
    Long partnerId,
    Long userId,
    String phoneNumber,
    String fullName,
    KycStatus kycStatus,
    String vehicleType,
    String vehicleNumber,
    String city,
    int totalDeliveries,
    LocalDateTime createdAt
) {}
