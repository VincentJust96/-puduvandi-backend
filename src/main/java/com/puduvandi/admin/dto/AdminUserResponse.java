package com.puduvandi.admin.dto;

import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "User summary for admin view")
public record AdminUserResponse(
    Long id,
    String phoneNumber,
    String fullName,
    String email,
    UserRole role,
    UserStatus status,
    KycStatus kycStatus,
    LocalDateTime createdAt
) {}
