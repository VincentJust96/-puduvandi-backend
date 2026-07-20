package com.puduvandi.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to complete delivery partner profile")
public record CompletePartnerProfileRequest(

    @NotBlank(message = "Vehicle type is required")
    @Schema(example = "Two Wheeler")
    String vehicleType,

    @NotBlank(message = "Vehicle number is required")
    @Schema(example = "TN-31-AB-1234")
    String vehicleNumber,

    @NotBlank(message = "City is required")
    @Schema(example = "Puduvandi")
    String city

) {}
