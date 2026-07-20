package com.puduvandi.auth.dto;

import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Basic user info returned inside AuthTokenResponse.
 */
@Schema(description = "Basic user information")
public record UserSummary(

    Long id,
    String phoneNumber,
    String email,
    String fullName,
    UserRole role,
    UserStatus status,

    @Schema(description = "True when the user just registered — frontend should show role-selection screen")
    boolean isNewUser,

    @Schema(description = "True when the user has completed their profile (fullName set)")
    boolean profileComplete

) {}
