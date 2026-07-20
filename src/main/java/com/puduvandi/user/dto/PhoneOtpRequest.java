package com.puduvandi.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Sends an OTP to a phone number — used both to add a first phone number
 * (email/password accounts) and to prove ownership of a new number before
 * requesting a change (existing phone accounts).
 */
@Schema(description = "Request to send an OTP to a phone number")
public record PhoneOtpRequest(

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
    @Schema(example = "9876543210")
    String phoneNumber

) {}
