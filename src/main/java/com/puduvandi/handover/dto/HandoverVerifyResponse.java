package com.puduvandi.handover.dto;

import com.puduvandi.common.enums.HandoverPurpose;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a handover OTP verification")
public record HandoverVerifyResponse(
        Long bookingId,
        HandoverPurpose purpose,
        boolean verified,
        String message
) {}
