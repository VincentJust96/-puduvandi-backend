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
import com.puduvandi.payment.service.PaymentService;
import com.puduvandi.push.service.WebPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
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

    @Transactional
    public DepositClaimResponse fileClaim(Long ownerUserId, Long bookingId, FileDepositClaimRequest request) {
        Booking booking = bookingRepository.findByIdAndOwner_UserIdAndDeletedFalse(bookingId, ownerUserId)
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
        return toResponse(claim);
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

    private void notifyDepositResolved(Booking booking) {
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
        DepositClaim claim = depositClaimRepository.findById(claimId)
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
