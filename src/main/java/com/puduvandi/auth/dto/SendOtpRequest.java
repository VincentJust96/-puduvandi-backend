package com.puduvandi.auth.dto;

import com.puduvandi.common.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Step 1: User sends phone number to receive OTP.
 * Role is no longer required — returning users have a role already;
 * new users pick their role after OTP verification.
 */
@Schema(description = "Request to send OTP to a phone number")
public record SendOtpRequest(

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
    @Schema(example = "9876543210")
    String phoneNumber

) {}
