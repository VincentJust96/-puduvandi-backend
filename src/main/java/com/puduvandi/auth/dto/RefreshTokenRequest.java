package com.puduvandi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Used to get a new access token using a valid refresh token.
 */
@Schema(description = "Refresh access token using a refresh token")
public record RefreshTokenRequest(

    @NotBlank(message = "Refresh token is required")
    @Schema(example = "your-refresh-token-uuid-here")
    String refreshToken

) {}
