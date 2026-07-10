package com.puduvandi.errorlog.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.errorlog.dto.ErrorLogResponse;
import com.puduvandi.errorlog.entity.ErrorLog;
import com.puduvandi.errorlog.repository.ErrorLogRepository;
import com.puduvandi.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/error-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Error Logs", description = "View application error logs for diagnosis")
@SecurityRequirement(name = "bearerAuth")
public class ErrorLogController {

    private final ErrorLogRepository errorLogRepository;

    @GetMapping
    @Operation(summary = "List error logs (newest first), filter by severity or entity")
    public ResponseEntity<ApiResponse<Page<ErrorLogResponse>>> list(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ErrorLog> results;

        if (severity != null) {
            results = errorLogRepository.findBySeverityOrderByCreatedAtDesc(severity.toUpperCase(), pageable);
        } else if (entityType != null && entityId != null) {
            results = errorLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
        } else if (userId != null) {
            results = errorLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else {
            results = errorLogRepository.findAllOrderByCreatedAtDesc(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success("Error logs fetched", results.map(this::toResponse)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single error log by ID")
    public ResponseEntity<ApiResponse<ErrorLogResponse>> getById(@PathVariable Long id) {
        ErrorLog log = errorLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ErrorLog", id));
        return ResponseEntity.ok(ApiResponse.success("Error log fetched", toResponse(log)));
    }

    private ErrorLogResponse toResponse(ErrorLog e) {
        return new ErrorLogResponse(
                e.getId(), e.getSeverity(), e.getErrorCode(),
                e.getErrorMessage(), e.getSource(),
                e.getEntityType(), e.getEntityId(), e.getUserId(),
                e.getRequestPath(), e.getRequestMethod(),
                e.getContext(), e.getStackTrace(), e.getCreatedAt()
        );
    }
}
