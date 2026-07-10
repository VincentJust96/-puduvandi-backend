package com.puduvandi.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocationResponse(
    BigDecimal latitude,
    BigDecimal longitude,
    LocalDateTime locationUpdatedAt
) {}
