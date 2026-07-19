package com.puduvandi.owner.dto;

import com.puduvandi.common.enums.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Owner KYC profile details")
public record OwnerProfileResponse(
    Long id,
    Long userId,
    String phoneNumber,
    String fullName,
    KycStatus kycStatus,
    String businessName,
    String gstin,
    String addressLine1,
    String addressLine2,
    String city,
    String state,
    String pincode,
    int totalBikes,
    LocalDateTime createdAt
) {}
