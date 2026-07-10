package com.puduvandi.errorlog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "error_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short machine-readable code, e.g. BOOKING_OVERLAP, FILE_STORE_FAIL */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    /** Fully-qualified class name or method that raised the error */
    @Column(name = "source", length = 200)
    private String source;

    /** ERROR (default), WARN, FATAL */
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private String severity = "ERROR";

    /** Arbitrary JSON context — bikeId, bookingId, filename, etc. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", columnDefinition = "jsonb")
    private String context;

    /** Domain object type affected, e.g. Booking, Bike, User */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** DB id of the affected record */
    @Column(name = "entity_id")
    private Long entityId;

    /** User who triggered the action (null for system/background errors) */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
