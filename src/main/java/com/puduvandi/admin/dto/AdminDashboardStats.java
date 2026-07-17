package com.puduvandi.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin dashboard summary statistics")
public record AdminDashboardStats(
    long totalCustomers,
    long totalOwners,
    long totalBikes,
    long pendingKycCount,
    long pendingBikeApprovals,
    long pendingLicenceApprovals,
    long activeBookings,
    long completedBookings,
    double commissionPercent,
    long totalPartners,
    long pendingPartnerKycCount
) {}
