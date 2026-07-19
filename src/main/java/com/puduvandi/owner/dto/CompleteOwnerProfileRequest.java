package com.puduvandi.owner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to complete owner KYC profile")
public record CompleteOwnerProfileRequest(

    @NotBlank(message = "Business name is required")
    @Schema(example = "Ravi Bike Rentals")
    String businessName,

    @Schema(example = "27AAPFU0939F1ZV")
    String gstin,

    @NotBlank(message = "Address is required")
    @Schema(example = "12 Main Street")
    String addressLine1,

    @Schema(example = "Near Bus Stand")
    String addressLine2,

    @NotBlank(message = "City is required")
    @Schema(example = "Puduvandi")
    String city,

    @NotBlank(message = "State is required")
    @Schema(example = "Tamil Nadu")
    String state,

    @NotBlank(message = "Pincode is required")
    @Schema(example = "607402")
    String pincode

) {}
