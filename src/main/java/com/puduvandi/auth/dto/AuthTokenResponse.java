package com.puduvandi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned after successful OTP verification or token refresh.
 * Contains both access token and refresh token.
 */
@Schema(description = "JWT token pair returned after successful login")
public record AuthTokenResponse(

    @Schema(description = "Short-lived access token (15 min)", example = "eyJhbGci...")
    String accessToken,

    @Schema(description = "Long-lived refresh token (7 days)", example = "uuid-string")
    String refreshToken,

    @Schema(description = "Token type", example = "Bearer")
    String tokenType,

    @Schema(description = "User details")
    UserSummary user

) {
    /**
     * Convenience constructor — tokenType defaults to "Bearer".
     */
    public AuthTokenResponse(String accessToken, String refreshToken, UserSummary user) {
        this(accessToken, refreshToken, "Bearer", user);
    }
}
