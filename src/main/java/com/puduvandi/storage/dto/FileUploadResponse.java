package com.puduvandi.storage.dto;

import java.time.LocalDateTime;

public record FileUploadResponse(
    Long fileId,
    String fileUrl,
    String contentType,
    String originalFilename,
    Long fileSize,
    LocalDateTime uploadedAt
) {}
