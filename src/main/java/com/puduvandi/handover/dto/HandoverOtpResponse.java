package com.puduvandi.handover.dto;

import com.puduvandi.common.enums.HandoverPurpose;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Generated handover OTP — for in-app display only, never sent via SMS/WhatsApp")
public record HandoverOtpResponse(
        Long otpId,
        Long bookingId,
        HandoverPurpose purpose,
        @Schema(example = "482913") String otp,
        LocalDateTime expiresAt
) {}
