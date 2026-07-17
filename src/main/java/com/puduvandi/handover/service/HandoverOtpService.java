package com.puduvandi.handover.service;

import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.booking.service.BookingService;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DeliveryType;
import com.puduvandi.common.enums.HandoverPurpose;
import com.puduvandi.delivery.entity.DeliveryOrder;
import com.puduvandi.delivery.repository.DeliveryOrderRepository;
import com.puduvandi.delivery.service.DeliveryService;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.handover.dto.HandoverOtpResponse;
import com.puduvandi.handover.dto.HandoverVerifyResponse;
import com.puduvandi.handover.entity.HandoverOtp;
import com.puduvandi.handover.repository.HandoverOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dedicated OTP flow for the physical bike handover steps (pickup / return,
 * self or via delivery partner) — separate from the login OTP in AuthService,
 * since OtpRecord has no booking linkage and this flow has very different
 * rules (booking-scoped, role-pair validated, in-app-only, drives a state
 * transition on success).
 * <p>
 * The generated code is NEVER sent via SMS/WhatsApp/NotificationService —
 * it is only ever returned in the generate() response for in-app display,
 * by product design (both parties are expected to be physically together
 * for the handover).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoverOtpService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long OTP_VALIDITY_MINUTES = 5;

    private final HandoverOtpRepository handoverOtpRepository;
    private final BookingRepository bookingRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final BookingService bookingService;
    private final DeliveryService deliveryService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public HandoverOtpResponse generate(Long bookingId, HandoverPurpose purpose, Long requestingUserId) {
        Booking booking = loadBooking(bookingId);

        validateDeliveryTypeCompatible(booking, purpose);
        validateBookingStateForPurpose(booking, purpose);
        validatePaymentComplete(booking, purpose);

        Long expectedGeneratorId = generatorExpectedUserId(booking, purpose);
        if (!expectedGeneratorId.equals(requestingUserId)) {
            throw new BusinessException("You are not authorised to generate an OTP for this handover step.");
        }

        // Invalidate any prior unused OTPs for this booking+purpose before issuing a new one.
        List<HandoverOtp> priorUnused = handoverOtpRepository
                .findByBookingIdAndPurposeAndUsedFalse(bookingId, purpose);
        for (HandoverOtp prior : priorUnused) {
            prior.setUsed(true);
            handoverOtpRepository.save(prior);
        }

        String code = generateSixDigitCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES);

        HandoverOtp otp = HandoverOtp.builder()
                .bookingId(bookingId)
                .purpose(purpose)
                .otpCode(code)
                .generatedByUserId(requestingUserId)
                .expiresAt(expiresAt)
                .used(false)
                .failedAttempts(0)
                .build();
        handoverOtpRepository.save(otp);

        log.info("Handover OTP generated: bookingId={}, purpose={}, generatedBy={}", bookingId, purpose, requestingUserId);

        return new HandoverOtpResponse(otp.getId(), bookingId, purpose, code, expiresAt);
    }

    @Transactional
    public HandoverVerifyResponse verify(Long bookingId, HandoverPurpose purpose, String code, Long requestingUserId) {
        Booking booking = loadBooking(bookingId);

        validateDeliveryTypeCompatible(booking, purpose);

        Long expectedValidatorId = validatorExpectedUserId(booking, purpose);
        if (!expectedValidatorId.equals(requestingUserId)) {
            throw new BusinessException("You are not authorised to verify an OTP for this handover step.");
        }

        HandoverOtp otp = handoverOtpRepository
                .findLatestActive(bookingId, purpose, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException(
                        "No active OTP found for this handover step. Please generate a new one."));

        if (!otp.getOtpCode().equals(code)) {
            otp.setFailedAttempts(otp.getFailedAttempts() + 1);
            boolean lockedOut = otp.getFailedAttempts() >= MAX_FAILED_ATTEMPTS;
            if (lockedOut) {
                // Invalidate — forces the generator to issue a fresh OTP.
                otp.setUsed(true);
            }
            handoverOtpRepository.save(otp);

            if (lockedOut) {
                throw new BusinessException(
                        "Incorrect OTP. Maximum attempts exceeded — please generate a new OTP.");
            }
            int remaining = MAX_FAILED_ATTEMPTS - otp.getFailedAttempts();
            throw new BusinessException("Incorrect OTP. " + remaining + " attempt(s) remaining.");
        }

        otp.setUsed(true);
        otp.setUsedAt(LocalDateTime.now());
        otp.setVerifiedByUserId(requestingUserId);
        handoverOtpRepository.save(otp);

        applyTransition(booking, purpose);

        log.info("Handover OTP verified: bookingId={}, purpose={}, verifiedBy={}", bookingId, purpose, requestingUserId);

        return new HandoverVerifyResponse(bookingId, purpose, true, "Handover verified successfully.");
    }

    // ===== State transition dispatch =====

    private void applyTransition(Booking booking, HandoverPurpose purpose) {
        Long bookingId = booking.getId();
        switch (purpose) {
            case PICKUP_SELF -> bookingService.transitionToRideStarted(bookingId);
            case PICKUP_PARTNER -> deliveryService.transitionToPickedUp(requireDeliveryOrder(bookingId).getId());
            case RECEIVE_PARTNER -> {
                deliveryService.transitionToDelivered(requireDeliveryOrder(bookingId).getId());
                bookingService.transitionToRideStarted(bookingId);
            }
            case RETURN_SELF -> bookingService.completeBooking(bookingId);
            case RETURN_TO_PARTNER -> deliveryService.transitionToReturnCollected(requireDeliveryOrder(bookingId).getId());
            case RETURN_FINAL -> {
                deliveryService.transitionToReturnCompleted(requireDeliveryOrder(bookingId).getId());
                bookingService.completeBooking(bookingId);
            }
        }
    }

    // ===== Role/identity resolution =====

    private Long generatorExpectedUserId(Booking booking, HandoverPurpose purpose) {
        return switch (purpose) {
            case PICKUP_SELF, RECEIVE_PARTNER, RETURN_TO_PARTNER -> booking.getCustomer().getId();
            case PICKUP_PARTNER -> claimedPartnerId(booking.getId());
            case RETURN_SELF, RETURN_FINAL -> booking.getOwner().getUser().getId();
        };
    }

    private Long validatorExpectedUserId(Booking booking, HandoverPurpose purpose) {
        return switch (purpose) {
            case PICKUP_SELF, PICKUP_PARTNER -> booking.getOwner().getUser().getId();
            case RETURN_SELF -> booking.getCustomer().getId();
            case RECEIVE_PARTNER, RETURN_TO_PARTNER, RETURN_FINAL -> claimedPartnerId(booking.getId());
        };
    }

    private Long claimedPartnerId(Long bookingId) {
        DeliveryOrder order = requireDeliveryOrder(bookingId);
        if (order.getPartner() == null) {
            throw new BusinessException("No delivery partner has claimed this booking yet.");
        }
        return order.getPartner().getId();
    }

    // ===== Validation helpers =====

    private void validateDeliveryTypeCompatible(Booking booking, HandoverPurpose purpose) {
        DeliveryType required = switch (purpose) {
            case PICKUP_SELF, RETURN_SELF -> DeliveryType.SELF_PICKUP;
            case PICKUP_PARTNER, RECEIVE_PARTNER, RETURN_TO_PARTNER, RETURN_FINAL -> DeliveryType.PARTNER_DELIVERY;
        };
        if (booking.getDeliveryType() != required) {
            throw new BusinessException("This handover step is not applicable to this booking.");
        }
    }

    private void validateBookingStateForPurpose(Booking booking, HandoverPurpose purpose) {
        switch (purpose) {
            case PICKUP_SELF, RECEIVE_PARTNER -> requireBookingStatus(booking, BookingStatus.CONFIRMED);
            case PICKUP_PARTNER -> {
                requireBookingStatus(booking, BookingStatus.CONFIRMED);
                claimedPartnerId(booking.getId()); // throws if not yet claimed
            }
            case RETURN_SELF, RETURN_TO_PARTNER, RETURN_FINAL -> requireBookingStatus(booking, BookingStatus.RETURN_REQUESTED);
        }
    }

    /**
     * A booking booked under the DEPOSIT plan (10% up front) must have its balance cleared
     * before the bike actually changes hands — gates every pickup-leg purpose (not returns,
     * which happen after the ride regardless of how it was paid for).
     */
    private void validatePaymentComplete(Booking booking, HandoverPurpose purpose) {
        boolean isPickupLeg = switch (purpose) {
            case PICKUP_SELF, PICKUP_PARTNER, RECEIVE_PARTNER -> true;
            case RETURN_SELF, RETURN_TO_PARTNER, RETURN_FINAL -> false;
        };
        if (!isPickupLeg) {
            return;
        }
        BigDecimal balanceDue = booking.getTotalAmount().subtract(booking.getAmountPaid());
        if (balanceDue.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                    "The remaining balance of ₹" + balanceDue + " must be paid before pickup.");
        }
    }

    private void requireBookingStatus(Booking booking, BookingStatus required) {
        if (booking.getStatus() != required) {
            throw new BusinessException(
                    "This handover step requires the booking to be " + required
                    + ". Current status: " + booking.getStatus());
        }
    }

    private Booking loadBooking(Long bookingId) {
        return bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    private DeliveryOrder requireDeliveryOrder(Long bookingId) {
        return deliveryOrderRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryOrder for booking", bookingId));
    }

    private String generateSixDigitCode() {
        int value = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}
