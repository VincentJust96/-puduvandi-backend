package com.puduvandi.errorlog.service;

import com.puduvandi.errorlog.entity.ErrorLog;
import com.puduvandi.errorlog.repository.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Persists failure records to error_logs.
 *
 * Rules:
 * - Never throws — a logging call must never break the caller.
 * - Uses REQUIRES_NEW so a rolled-back parent transaction doesn't discard the log row.
 * - Use the fluent builder methods for the common call sites.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorLogService {

    private final ErrorLogRepository errorLogRepository;

    // ===== Fluent entry points =====

    /** Log from the global exception handler (has full HTTP context). */
    public void logApiError(Throwable ex, String requestPath, String requestMethod, Long userId) {
        save(ErrorLog.builder()
                .severity("ERROR")
                .errorCode(ex.getClass().getSimpleName())
                .errorMessage(ex.getMessage())
                .stackTrace(toStackTrace(ex))
                .source(firstRelevantFrame(ex))
                .requestPath(requestPath)
                .requestMethod(requestMethod)
                .userId(userId)
                .build());
    }

    /** Log a service-level failure with entity context. */
    public void logServiceError(Throwable ex, String entityType, Long entityId, Long userId) {
        save(ErrorLog.builder()
                .severity("ERROR")
                .errorCode(ex.getClass().getSimpleName())
                .errorMessage(ex.getMessage())
                .stackTrace(toStackTrace(ex))
                .source(firstRelevantFrame(ex))
                .entityType(entityType)
                .entityId(entityId)
                .userId(userId)
                .build());
    }

    /** Log with a custom error code and JSON context string. */
    public void logWithCode(String severity, String errorCode, String message,
                            String source, String entityType, Long entityId,
                            Long userId, String requestPath, String requestMethod,
                            String context) {
        save(ErrorLog.builder()
                .severity(severity)
                .errorCode(errorCode)
                .errorMessage(message)
                .source(source)
                .entityType(entityType)
                .entityId(entityId)
                .userId(userId)
                .requestPath(requestPath)
                .requestMethod(requestMethod)
                .context(context)
                .build());
    }

    // ===== Internal =====

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(ErrorLog errorLog) {
        try {
            errorLogRepository.save(errorLog);
        } catch (Exception ex) {
            // Last resort: write to application log so the error isn't silently lost
            log.error("CRITICAL: Failed to persist error log entry. Original error: {}. Save error: {}",
                    errorLog.getErrorMessage(), ex.getMessage());
        }
    }

    private String toStackTrace(Throwable ex) {
        if (ex == null) return null;
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        // Cap at 4000 chars to stay within typical TEXT column practical limits
        return trace.length() > 4000 ? trace.substring(0, 4000) + "\n... truncated" : trace;
    }

    private String firstRelevantFrame(Throwable ex) {
        if (ex == null || ex.getStackTrace().length == 0) return null;
        for (StackTraceElement frame : ex.getStackTrace()) {
            if (frame.getClassName().startsWith("com.puduvandi")) {
                return frame.getClassName() + "." + frame.getMethodName()
                        + "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")";
            }
        }
        return ex.getStackTrace()[0].toString();
    }
}
