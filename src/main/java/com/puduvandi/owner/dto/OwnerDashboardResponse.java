package com.puduvandi.owner.dto;

import java.math.BigDecimal;

public record OwnerDashboardResponse(
        long totalBikes,
        long totalBookings,
        long activeBookings,
        BigDecimal totalEarnings
) {}
