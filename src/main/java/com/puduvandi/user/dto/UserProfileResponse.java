package com.puduvandi.user.dto;

import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Full user profile response returned to the customer.
 */
@Schema(description = "User profile details")
public record UserProfileResponse(
    Long id,
    String phoneNumber,
    String fullName,
    String email,
    String profileImageUrl,
    UserRole role,
    UserStatus status,
    KycStatus kycStatus,
    LocalDateTime createdAt
) {}
