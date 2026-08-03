package com.puduvandi.deposit.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DepositStatus;
import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.deposit.dto.DepositClaimResponse;
import com.puduvandi.deposit.dto.FailedRefundResponse;
import com.puduvandi.deposit.dto.FileDepositClaimRequest;
import com.puduvandi.deposit.entity.DepositClaim;
import com.puduvandi.deposit.repository.DepositClaimRepository;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.audit.service.AdminAuditService;
import com.puduvandi.payment.service.PaymentService;
import com.puduvandi.push.service.WebPushService;
import com.puduvandi.realtime.RealtimeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Owner files a deduction claim on a completed booking's security deposit;
 * an admin approves (deduction goes through as filed) or rejects it (full
 * refund) — owners never get unilateral power over a customer's money.
 * Actual Razorpay refund/mock handling lives in PaymentService.refundDeposit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositClaimService {

    private final DepositClaimRepository depositClaimRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final WebPushService webPushService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AdminAuditService adminAuditService;

    // Self-reference (lazily resolved to the Spring proxy) so instantRefund()'s call to
    // claimForInstantRefund() actually goes through a REAL separate transaction rather than a
    // plain self-invocation — required for its own propagation to take effect. See
    // claimForInstantRefund's javadoc for why this split exists, and PaymentService.self for the
    // same pattern.
    @Autowired
    @Lazy
    private DepositClaimService self;

    @Transactional
    public DepositClaimResponse fileClaim(Long ownerUserId, Long bookingId, FileDepositClaimRequest request) {
        bookingRepository.findByIdAndOwner_UserIdAndDeletedFalse(bookingId, ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        // Lock the booking row so two concurrent claim-filing requests for the
        // same booking can't both pass the depositStatus==HELD check below.
        Booking booking = bookingRepository.lockById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessException("A deposit claim can only be filed after the booking is completed.");
        }
        if (booking.getDepositStatus() != DepositStatus.HELD) {
            throw new BusinessException("This booking's deposit is not eligible for a claim (status: "
                    + booking.getDepositStatus() + ").");
        }
        if (request.deductionAmount().compareTo(booking.getSecurityDeposit()) > 0) {
            throw new BusinessException("Deduction amount cannot exceed the booking's security deposit of "
                    + booking.getSecurityDeposit());
        }

        User owner = userRepository.getReferenceById(ownerUserId);

        DepositClaim claim = depositClaimRepository.save(DepositClaim.builder()
                .booking(booking)
                .filedByOwner(owner)
                .deductionAmount(request.deductionAmount())
                .reason(request.reason())
                .photoUrls(joinPhotoUrls(request.photoUrls()))
                .status(DocumentStatus.PENDING)
                .build());

        booking.setDepositStatus(DepositStatus.CLAIM_PENDING);
        bookingRepository.save(booking);

        log.info("Deposit claim filed: claimId={}, bookingId={}, ownerUserId={}, deductionAmount={}",
                claim.getId(), bookingId, ownerUserId, request.deductionAmount());
        publishDepositClaimUpdate(booking);
        return toResponse(claim);
    }

    /**
     * Owner-triggered instant full refund — skips the claim/admin-review path
     * entirely for bookings where the owner has nothing to dispute. Only
     * available while the deposit is still HELD (same guard as fileClaim).
     * <p>
     * Deliberately NOT itself @Transactional — see claimForInstantRefund's javadoc for why the
     * locking step has to commit (and release its row lock) before paymentService.refundDeposit()
     * runs, rather than both sharing one transaction here.
     */
    public void instantRefund(Long ownerUserId, Long bookingId) {
        Booking booking = self.claimForInstantRefund(ownerUserId, bookingId);

        paymentService.refundDeposit(booking, booking.getSecurityDeposit());
        notifyDepositResolved(booking);

        log.info("Instant full deposit refund: bookingId={}, ownerUserId={}", bookingId, ownerUserId);
        publishDepositClaimUpdate(booking);
    }

    /**
     * Validates and "claims" a booking for instantRefund() by flipping it straight to
     * REFUND_INITIATED — in its own short transaction, separate from the actual refund call.
     * <p>
     * Why: this takes a pessimistic lock on the SAME booking row that
     * paymentService.refundDeposit() (REQUIRES_NEW — a different DB connection) then needs to
     * UPDATE. If both ran in one transaction, that connection would still be holding this lock
     * when refundDeposit's separate connection tried to write the row — a self-inflicted wait
     * that (confirmed live) always hits the DB's lock_timeout and fails, because the original
     * connection can't release the lock until instantRefund() returns, which can't happen until
     * refundDeposit() (blocked on that very lock) returns. Committing here first releases the
     * lock before refundDeposit() ever touches the row, so there's nothing left to contend with.
     * <p>
     * Flipping straight to REFUND_INITIATED (rather than just validating and leaving it HELD)
     * is what keeps this safe under concurrency: a second simultaneous instantRefund() call's own
     * claimForInstantRefund() will see depositStatus != HELD once it acquires the lock, and reject
     * — the same protection the old single-transaction version got from never releasing the lock
     * at all, just achieved by state instead of by holding the lock across the whole operation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Booking claimForInstantRefund(Long ownerUserId, Long bookingId) {
        bookingRepository.findByIdAndOwner_UserIdAndDeletedFalse(bookingId, ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        // Lock the booking row so this can't race a concurrent claim-filing
        // request for the same booking (same reasoning as fileClaim above).
        Booking booking = bookingRepository.lockById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessException("A deposit can only be refunded after the booking is completed.");
        }
        if (booking.getDepositStatus() != DepositStatus.HELD) {
            throw new BusinessException("This booking's deposit is not eligible for an instant refund (status: "
                    + booking.getDepositStatus() + ").");
        }

        booking.setDepositStatus(DepositStatus.REFUND_INITIATED);
        return bookingRepository.save(booking);
    }

    @Transactional(readOnly = true)
    public Page<DepositClaimResponse> listClaims(DocumentStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return depositClaimRepository.findAllForAdmin(status, pageable).map(this::toResponse);
    }

    @Transactional
    public DepositClaimResponse approveClaim(Long adminUserId, Long claimId) {
        DepositClaim claim = findPendingClaim(claimId);
        Booking booking = claim.getBooking();

        paymentService.refundDeposit(booking, booking.getSecurityDeposit().subtract(claim.getDeductionAmount()));
        notifyDepositResolved(booking);

        claim.setStatus(DocumentStatus.APPROVED);
        claim.setDecidedByAdmin(userRepository.getReferenceById(adminUserId));
        claim.setDecidedAt(LocalDateTime.now());
        DepositClaim saved = depositClaimRepository.save(claim);

        log.info("Deposit claim approved: claimId={}, bookingId={}, adminUserId={}", claimId, booking.getId(), adminUserId);
        adminAuditService.recordCurrentActor("APPROVE_DEPOSIT_CLAIM", "DepositClaim", String.valueOf(claimId),
                java.util.Map.of("status", "PENDING"),
                java.util.Map.of("status", "APPROVED", "deductionAmount", claim.getDeductionAmount()));
        publishDepositClaimUpdate(booking);
        return toResponse(saved);
    }

    @Transactional
    public DepositClaimResponse rejectClaim(Long adminUserId, Long claimId, String reason) {
        DepositClaim claim = findPendingClaim(claimId);
        Booking booking = claim.getBooking();

        // The claim was deemed invalid — the customer gets their full deposit back.
        paymentService.refundDeposit(booking, booking.getSecurityDeposit());
        notifyDepositResolved(booking);

        claim.setStatus(DocumentStatus.REJECTED);
        claim.setAdminRejectionReason(reason);
        claim.setDecidedByAdmin(userRepository.getReferenceById(adminUserId));
        claim.setDecidedAt(LocalDateTime.now());
        DepositClaim saved = depositClaimRepository.save(claim);

        log.info("Deposit claim rejected: claimId={}, bookingId={}, adminUserId={}, reason={}",
                claimId, booking.getId(), adminUserId, reason);
        adminAuditService.recordCurrentActor("REJECT_DEPOSIT_CLAIM", "DepositClaim", String.valueOf(claimId),
                java.util.Map.of("status", "PENDING"),
                java.util.Map.of("status", "REJECTED", "reason", reason));
        publishDepositClaimUpdate(booking);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<FailedRefundResponse> listFailedRefunds(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return bookingRepository.findByDepositStatusAndDeletedFalseOrderByUpdatedAtDesc(DepositStatus.REFUND_FAILED, pageable)
                .map(b -> new FailedRefundResponse(
                        b.getId(), b.getBookingReference(), b.getSecurityDeposit(), b.getDepositRefundAmount(), b.getUpdatedAt()));
    }

    /** Re-attempts a previously-failed deposit refund for the same amount that failed. */
    @Transactional
    public void retryFailedRefund(Long bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getDepositStatus() != DepositStatus.REFUND_FAILED) {
            throw new BusinessException("This booking's deposit is not in a failed state (status: "
                    + booking.getDepositStatus() + ").");
        }
        BigDecimal amount = booking.getDepositRefundAmount();
        if (amount == null) {
            throw new BusinessException("No recorded refund amount to retry for this booking.");
        }
        paymentService.refundDeposit(booking, amount);
    }

    /**
     * Only pushes once the deposit is definitively REFUNDED — for a real Razorpay refund,
     * refundDeposit() leaves the booking at REFUND_INITIATED and PaymentService itself pushes
     * this same notification later, once handleRefundWebhookEvent() confirms settlement (see
     * PaymentService.pushDepositResolvedNotification). Nothing to tell the customer yet for
     * REFUND_INITIATED, and REFUND_FAILED is an admin-facing problem (retryFailedRefund), not
     * a customer-facing "resolved" event.
     */
    private void publishDepositClaimUpdate(Booking booking) {
        try {
            realtimeEventPublisher.depositClaimUpdated(booking.getId(),
                    booking.getCustomer().getId(), booking.getOwner().getUser().getId(),
                    booking.getDepositStatus().name());
        } catch (Exception ex) {
            log.warn("Failed to publish realtime deposit-claim update for bookingId={}", booking.getId(), ex);
        }
    }

    private void notifyDepositResolved(Booking booking) {
        if (booking.getDepositStatus() != DepositStatus.REFUNDED) {
            return;
        }
        try {
            BigDecimal refunded = booking.getDepositRefundAmount();
            String body = refunded == null
                    ? "Your deposit has been resolved."
                    : "₹" + refunded + " of your ₹" + booking.getSecurityDeposit() + " deposit has been refunded.";
            webPushService.sendToUser(booking.getCustomer().getId(), "Deposit resolved", body, "/bookings");
        } catch (Exception ex) {
            log.warn("Failed to push deposit-resolved notification for bookingId={}", booking.getId(), ex);
        }
    }

    private String joinPhotoUrls(List<String> photoUrls) {
        if (photoUrls == null || photoUrls.isEmpty()) return null;
        return photoUrls.stream().filter(u -> u != null && !u.isBlank()).collect(Collectors.joining(","));
    }

    private List<String> splitPhotoUrls(String photoUrls) {
        if (photoUrls == null || photoUrls.isBlank()) return Collections.emptyList();
        return List.of(photoUrls.split(","));
    }

    private DepositClaim findPendingClaim(Long claimId) {
        // Locked so two concurrent approve/reject calls (double-click, retried
        // request) on the same claim can't both pass the PENDING check and
        // both trigger a refund.
        DepositClaim claim = depositClaimRepository.lockById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("DepositClaim", claimId));
        if (claim.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException("This claim has already been decided (status: " + claim.getStatus() + ").");
        }
        return claim;
    }

    private DepositClaimResponse toResponse(DepositClaim c) {
        Booking booking = c.getBooking();
        return new DepositClaimResponse(
                c.getId(),
                booking.getId(),
                booking.getBookingReference(),
                c.getFiledByOwner().getId(),
                c.getFiledByOwner().getFullName(),
                booking.getSecurityDeposit(),
                c.getDeductionAmount(),
                c.getReason(),
                splitPhotoUrls(c.getPhotoUrls()),
                c.getStatus(),
                c.getAdminRejectionReason(),
                c.getDecidedAt(),
                c.getCreatedAt()
        );
    }
}
