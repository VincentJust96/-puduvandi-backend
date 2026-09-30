package com.puduvandi.tripexpense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row in the "Your Trips" grid. status is derived, not stored — see TripExpenseService.deriveStatus. */
public record TripSummaryResponse(
    Long id,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal budget,
    BigDecimal spent,
    int travelers,
    String status,
    boolean creator
) {}
