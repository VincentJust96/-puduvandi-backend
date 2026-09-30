package com.puduvandi.deposit.entity;

import com.puduvandi.auth.entity.User;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.common.entity.BaseEntity;
import com.puduvandi.common.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An owner's request to deduct part of a completed booking's security
 * deposit — sits PENDING until an admin approves (deduction goes through as
 * filed) or rejects it (deposit refunds in full). See DepositClaimService.
 */
@Entity
@Table(name = "deposit_claims")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositClaim extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filed_by_owner_id", nullable = false)
    private User filedByOwner;

    @Column(name = "deduction_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal deductionAmount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** Comma-joined damage-evidence photo URLs — see DepositClaimService for the list<->string mapping. */
    @Column(name = "photo_urls", length = 2000)
    private String photoUrls;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "admin_rejection_reason", length = 500)
    private String adminRejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_admin_id")
    private User decidedByAdmin;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
}
