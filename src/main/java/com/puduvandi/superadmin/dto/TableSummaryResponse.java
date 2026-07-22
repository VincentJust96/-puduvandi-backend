package com.puduvandi.superadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One row of the live table list")
public record TableSummaryResponse(
    String name,
    long rowCount,
    boolean editable
) {}
