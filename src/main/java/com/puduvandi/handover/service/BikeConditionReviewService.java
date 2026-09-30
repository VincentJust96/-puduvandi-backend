package com.puduvandi.handover.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.handover.dto.ConditionReviewResponse;
import com.puduvandi.handover.dto.SubmitConditionReviewRequest;
import com.puduvandi.handover.entity.BikeConditionReview;
import com.puduvandi.handover.repository.BikeConditionReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Customer's pre-pickup bike condition snapshot — photos, fuel level,
 * odometer reading — filed once per booking right before generating a
 * pickup OTP (see HandoverOtpService.generate, which requires this to exist
 * for PICKUP_SELF/RECEIVE_PARTNER). Gives the owner a timestamped baseline
 * to check against if they later raise a deposit/damage claim.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BikeConditionReviewService {

    private final BikeConditionReviewRepository conditionReviewRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional
    public ConditionReviewResponse submit(Long customerUserId, Long bookingId, SubmitConditionReviewRequest request) {
        Booking booking = bookingRepository.findByIdAndCustomerIdAndDeletedFalse(bookingId, customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("A condition review can only be filed while the booking is confirmed and awaiting pickup.");
        }
        if (conditionReviewRepository.existsByBookingId(bookingId)) {
            throw new BusinessException("A condition review has already been filed for this booking.");
        }

        User customer = userRepository.getReferenceById(customerUserId);

        BikeConditionReview review = conditionReviewRepository.save(BikeConditionReview.builder()
                .booking(booking)
                .customer(customer)
                .fuelLevel(request.fuelLevel())
                .odometerKm(request.odometerKm())
                .photoUrls(joinPhotoUrls(request.photoUrls()))
                .build());

        log.info("Bike condition review filed: reviewId={}, bookingId={}, customerUserId={}",
                review.getId(), bookingId, customerUserId);
        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public ConditionReviewResponse get(Long requestingUserId, Long bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        boolean isCustomer = booking.getCustomer().getId().equals(requestingUserId);
        boolean isOwner = booking.getOwner().getUser().getId().equals(requestingUserId);
        if (!isCustomer && !isOwner) {
            throw new ResourceNotFoundException("Booking", bookingId);
        }

        BikeConditionReview review = conditionReviewRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Condition review for booking", bookingId));
        return toResponse(review);
    }

    private String joinPhotoUrls(List<String> photoUrls) {
        return photoUrls.stream().filter(u -> u != null && !u.isBlank()).collect(Collectors.joining(","));
    }

    private List<String> splitPhotoUrls(String photoUrls) {
        if (photoUrls == null || photoUrls.isBlank()) return Collections.emptyList();
        return List.of(photoUrls.split(","));
    }

    private ConditionReviewResponse toResponse(BikeConditionReview r) {
        return new ConditionReviewResponse(
                r.getId(),
                r.getBooking().getId(),
                r.getFuelLevel(),
                r.getOdometerKm(),
                splitPhotoUrls(r.getPhotoUrls()),
                r.getCreatedAt()
        );
    }
}
