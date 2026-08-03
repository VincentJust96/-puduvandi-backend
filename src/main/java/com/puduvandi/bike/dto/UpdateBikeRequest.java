package com.puduvandi.bike.dto;

import com.puduvandi.common.enums.FuelType;
import com.puduvandi.common.enums.TransmissionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdateBikeRequest(
    @NotBlank(message = "Brand is required") String brand,
    @NotBlank(message = "Model is required") String model,
    @NotNull @Min(2000) @Max(2030) Integer year,
    @NotNull FuelType fuelType,
    TransmissionType transmission,
    Integer engineCapacity,
    boolean helmetIncluded,
    // Boolean (not boolean): null means "unspecified, leave the bike's current
    // value alone" — same null-guard convention already used for latitude/area
    // below, needed because a primitive silently defaults to false and would
    // flip these flags off on any update from a client that omits them.
    Boolean papersIncluded,
    Boolean fuelIncluded,
    Boolean roadsideAssistance,
    @NotNull @Positive BigDecimal pricePerHour,
    @NotNull @Positive BigDecimal pricePerDay,
    @NotNull @PositiveOrZero BigDecimal securityDeposit,
    String description,
    List<String> imageUrls,
    BigDecimal latitude,
    BigDecimal longitude,
    String area,
    String rcDocumentUrl,
    String insuranceDocumentUrl,
    String insurancePolicyNumber,
    @Future(message = "Insurance expiry date must be in the future")
    LocalDate insuranceExpiryDate,
    String insuranceDocumentPassword
) {}
