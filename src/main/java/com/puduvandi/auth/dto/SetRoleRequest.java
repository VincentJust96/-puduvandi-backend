package com.puduvandi.auth.dto;

import com.puduvandi.common.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /auth/set-role.
 * Called once by a new user after OTP verification to pick CUSTOMER or OWNER.
 */
@Schema(description = "Set role for a newly registered user")
public record SetRoleRequest(

    @NotNull(message = "Role is required")
    @Schema(example = "CUSTOMER")
    UserRole role

) {}
