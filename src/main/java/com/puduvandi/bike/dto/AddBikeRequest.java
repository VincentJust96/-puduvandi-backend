package com.puduvandi.bike.dto;

import com.puduvandi.common.enums.FuelType;
import com.puduvandi.common.enums.TransmissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Request to add a new bike listing")
public record AddBikeRequest(

    @NotBlank(message = "Brand is required")
    @Schema(example = "Royal Enfield")
    String brand,

    @NotBlank(message = "Model is required")
    @Schema(example = "Classic 350")
    String model,

    @NotNull @Min(2000) @Max(2030)
    @Schema(example = "2022")
    Integer year,

    @NotBlank(message = "Registration number is required")
    @Schema(example = "TN09AB1234")
    String registrationNumber,

    @NotNull(message = "Fuel type is required")
    @Schema(example = "PETROL")
    FuelType fuelType,

    @Schema(example = "MANUAL")
    TransmissionType transmission,

    @Schema(example = "350")
    Integer engineCapacity,

    @Schema(example = "true")
    boolean helmetIncluded,

    @NotNull @Positive(message = "Price per hour must be positive")
    @Schema(example = "80.00")
    BigDecimal pricePerHour,

    @NotNull @Positive(message = "Price per day must be positive")
    @Schema(example = "600.00")
    BigDecimal pricePerDay,

    @NotNull @PositiveOrZero
    @Schema(example = "2000.00")
    BigDecimal securityDeposit,

    @Schema(example = "A classic bike perfect for highway rides.")
    String description,

    @Schema(description = "List of image URLs (upload to CDN first)")
    List<String> imageUrls,

    @Schema(description = "Bike's pickup location, captured once from the owner's device — required for partner delivery to be offered on this bike", example = "12.9716")
    BigDecimal latitude,

    @Schema(example = "79.8589")
    BigDecimal longitude

) {}
