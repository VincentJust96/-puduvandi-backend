package com.puduvandi.admin.dto;

import com.puduvandi.common.enums.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Owner summary for admin KYC review")
public record AdminOwnerResponse(
    Long ownerId,
    Long userId,
    String phoneNumber,
    String fullName,
    KycStatus kycStatus,
    String businessName,
    String city,
    String state,
    int totalBikes,
    LocalDateTime createdAt
) {}
