package com.puduvandi.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Resolved live-tracking info for a booking — which actor (customer or " +
        "delivery partner) should currently be tracked, and their last known location")
public record TrackingResponse(
    @Schema(example = "PARTNER") String trackingRole,
    @Schema(example = "In Transit to Customer") String phaseLabel,
    BigDecimal latitude,
    BigDecimal longitude,
    LocalDateTime locationUpdatedAt,
    @Schema(description = "true if the last location update is older than 3 minutes (possible GPS interruption)")
    boolean stale
) {}
