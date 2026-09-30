package com.puduvandi.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puduvandi.audit.entity.AdminAuditLog;
import com.puduvandi.audit.repository.AdminAuditLogRepository;
import com.puduvandi.security.PuduvandiUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queryable audit trail for admin/superadmin destructive actions.
 * <p>
 * Rules (same as ErrorLogService):
 * - Never throws — an audit-logging call must never break the caller's real action.
 * - Uses REQUIRES_NEW so a rolled-back parent transaction doesn't discard the log row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void record(Long actorUserId, String actorRole, String action,
                        String entityType, String entityId,
                        Object beforeState, Object afterState) {
        save(AdminAuditLog.builder()
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .beforeState(toJson(beforeState))
                .afterState(toJson(afterState))
                .build());
    }

    /**
     * Convenience entry point for the many call sites deep inside admin/superadmin
     * service methods that don't take the acting admin's identity as a parameter —
     * reads it straight from the request's SecurityContext instead of threading a
     * principal through every method signature. Safe because every caller of this
     * runs only within an HTTP request already gated by @PreAuthorize("hasRole(...ADMIN)"),
     * so an authenticated admin/superadmin principal is always present at call time.
     */
    public void recordCurrentActor(String action, String entityType, String entityId,
                                    Object beforeState, Object afterState) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!(auth.getPrincipal() instanceof PuduvandiUserPrincipal principal)) {
                log.warn("Skipping audit log — no authenticated admin principal for action={}", action);
                return;
            }
            record(principal.getUserId(), principal.getRole(), action, entityType, entityId, beforeState, afterState);
        } catch (Exception ex) {
            log.warn("Failed to resolve current actor for audit log (non-fatal): {}", ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AdminAuditLog entry) {
        try {
            adminAuditLogRepository.save(entry);
        } catch (Exception ex) {
            log.error("CRITICAL: Failed to persist admin audit log entry. action={}, entityType={}, entityId={}. Save error: {}",
                    entry.getAction(), entry.getEntityType(), entry.getEntityId(), ex.getMessage());
        }
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Failed to serialize audit log state to JSON: {}", ex.getMessage());
            return null;
        }
    }
}
