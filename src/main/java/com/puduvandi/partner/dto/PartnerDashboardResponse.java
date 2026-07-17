package com.puduvandi.partner.dto;

import com.puduvandi.common.enums.KycStatus;

public record PartnerDashboardResponse(
    KycStatus kycStatus,
    long totalDeliveries
) {}
