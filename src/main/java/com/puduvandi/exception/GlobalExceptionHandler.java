package com.puduvandi.exception;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.errorlog.service.ErrorLogService;
import com.puduvandi.security.PuduvandiUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Central exception handler for all REST controllers.
 * Converts exceptions into standard ApiResponse format.
 * Persists unexpected failures to error_logs for fast diagnosis.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    @Lazy
    private final ErrorLogService errorLogService;

    // ===== Expected client errors — NOT logged to DB =====

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors()
                .stream().map(FieldError::getDefaultMessage).toList();
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", errors));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({UnauthorizedException.class, AuthenticationException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(RuntimeException ex) {
        log.warn("Unauthorized access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleForbidden(RuntimeException ex) {
        log.warn("Forbidden access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * A concurrent request slipped past an app-level check-then-act guard and hit a DB
     * unique/FK constraint instead (e.g. two simultaneous review submissions for the same
     * booking, or two simultaneous signups for the same phone/email). Reported as a normal
     * 409 rather than falling through to the generic 500 handler below.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        errorLogService.logApiError(ex, request.getRequestURI(), request.getMethod(), currentUserId());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("This action conflicts with an existing record. Please refresh and try again."));
    }

    /**
     * A concurrent request is holding the same row (another in-flight update, or — as seen live —
     * a debugger paused mid-transaction) past the DB's lock_timeout (see application.yml's
     * datasource.hikari.connection-init-sql). Expected under concurrent access, not a bug — treated
     * like DataIntegrityViolationException above: a normal 409, not logged to error_logs.
     */
    @ExceptionHandler({org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.QueryTimeoutException.class})
    public ResponseEntity<ApiResponse<Void>> handleLockTimeout(Exception ex) {
        log.warn("Lock/query timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("This booking is being updated by another request right now. Please try again in a moment."));
    }

    // ===== Errors worth persisting to DB =====

    /** 404 — logged as WARN; useful for detecting bad references from the frontend */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        errorLogService.logApiError(ex, request.getRequestURI(), request.getMethod(), currentUserId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    /** 500 — always logged as ERROR; these are unexpected bugs */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred", ex);
        errorLogService.logApiError(ex, request.getRequestURI(), request.getMethod(), currentUserId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }

    // ===== Helper =====

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PuduvandiUserPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }
}
