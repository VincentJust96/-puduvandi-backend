package com.puduvandi.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Verifies the OTP sent to a phone number, either to add it directly
 * (no existing phone) or to file a change request (existing phone).
 */
@Schema(description = "Request to verify an OTP sent to a phone number")
public record VerifyPhoneOtpRequest(

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
    @Schema(example = "9876543210")
    String phoneNumber,

    @NotBlank(message = "OTP is required")
    @Schema(example = "123456")
    String otp

) {}
