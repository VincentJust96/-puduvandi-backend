package com.puduvandi.superadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Metadata for a single column, read live from information_schema")
public record ColumnMetaResponse(
    String name,
    String dataType,
    boolean nullable,
    boolean primaryKey,
    boolean unique,
    boolean sensitive
) {}
