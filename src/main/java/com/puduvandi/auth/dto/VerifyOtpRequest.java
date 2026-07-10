package com.puduvandi.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Step 2: User sends phone + OTP to receive JWT tokens.
 */
@Schema(description = "Request to verify OTP and login")
public record VerifyOtpRequest(

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
    @Schema(example = "9876543210")
    String phoneNumber,

    @NotBlank(message = "OTP is required")
    @Schema(example = "123456")
    String otp

) {}
