package com.puduvandi.audit.controller;

import com.puduvandi.audit.dto.AdminAuditLogResponse;
import com.puduvandi.audit.entity.AdminAuditLog;
import com.puduvandi.audit.repository.AdminAuditLogRepository;
import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.common.dto.ApiResponse;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/audit-log")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin — Audit Log", description = "Queryable trail of admin/superadmin destructive actions")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditLogController {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List admin audit log entries (newest first), filter by actor or entity type")
    public ResponseEntity<ApiResponse<Page<AdminAuditLogResponse>>> list(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AdminAuditLog> results = adminAuditLogRepository.search(actorUserId, entityType, pageable);

        Map<Long, User> actorsById = fetchActorsFor(results.getContent());
        return ResponseEntity.ok(ApiResponse.success("Audit log fetched",
                results.map(a -> toResponse(a, actorsById.get(a.getActorUserId())))));
    }

    private Map<Long, User> fetchActorsFor(List<AdminAuditLog> entries) {
        List<Long> userIds = entries.stream()
                .map(AdminAuditLog::getActorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private AdminAuditLogResponse toResponse(AdminAuditLog a, User actor) {
        return new AdminAuditLogResponse(
                a.getId(), a.getActorUserId(), actor == null ? null : actor.getFullName(), a.getActorRole(),
                a.getAction(), a.getEntityType(), a.getEntityId(),
                a.getBeforeState(), a.getAfterState(), a.getCreatedAt()
        );
    }
}
