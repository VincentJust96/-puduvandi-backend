package com.puduvandi.owner.dto;

import java.math.BigDecimal;

public record OwnerDashboardResponse(
        long totalBikes,
        long totalBookings,
        long activeBookings,
        /** Net of commission — SUM(booking.ownerEarning) for COMPLETED bookings. */
        BigDecimal totalEarnings,
        /** Gross rent before commission — SUM(booking.baseAmount) for COMPLETED bookings. */
        BigDecimal totalRevenue,
        /** Real per-booking commission actually charged — SUM(booking.commissionAmount). */
        BigDecimal totalCommission
) {}
