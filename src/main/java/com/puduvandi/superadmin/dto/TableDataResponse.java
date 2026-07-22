package com.puduvandi.superadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "A page of raw rows from a table, plus the column metadata needed to render/edit them")
public record TableDataResponse(
    String tableName,
    List<ColumnMetaResponse> columns,
    List<Map<String, Object>> rows,
    long totalElements,
    int page,
    int size,
    boolean editable
) {}
