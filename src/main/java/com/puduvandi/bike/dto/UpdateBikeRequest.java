package com.puduvandi.bike.dto;

import com.puduvandi.common.enums.FuelType;
import com.puduvandi.common.enums.TransmissionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record UpdateBikeRequest(
    @NotBlank(message = "Brand is required") String brand,
    @NotBlank(message = "Model is required") String model,
    @NotNull @Min(2000) @Max(2030) Integer year,
    @NotNull FuelType fuelType,
    TransmissionType transmission,
    Integer engineCapacity,
    boolean helmetIncluded,
    @NotNull @Positive BigDecimal pricePerHour,
    @NotNull @Positive BigDecimal pricePerDay,
    @NotNull @PositiveOrZero BigDecimal securityDeposit,
    String description,
    List<String> imageUrls,
    BigDecimal latitude,
    BigDecimal longitude
) {}
