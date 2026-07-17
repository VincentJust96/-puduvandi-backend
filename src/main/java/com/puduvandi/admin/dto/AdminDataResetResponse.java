package com.puduvandi.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a local data reset")
public record AdminDataResetResponse(
    int nonAdminUsersRemoved,
    String environment
) {}
