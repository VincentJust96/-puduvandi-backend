package com.puduvandi.handover.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "OTP code to verify a handover step")
public record HandoverVerifyRequest(

        @NotBlank(message = "OTP is required")
        @Schema(example = "482913")
        String otp

) {}
