package com.puduvandi.review.service;

import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ConflictException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.review.dto.BikeReviewResponse;
import com.puduvandi.review.dto.ReviewResponse;
import com.puduvandi.review.dto.SubmitReviewRequest;
import com.puduvandi.review.entity.Review;
import com.puduvandi.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets a customer rate a trip once it's COMPLETED — one review per booking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public ReviewResponse submitReview(Long customerId, Long bookingId, SubmitReviewRequest request) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!booking.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException("You do not have permission to review this booking.");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessException("You can only rate a trip after it's completed.");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new ConflictException("You've already rated this trip.");
        }

        Review review = Review.builder()
                .booking(booking)
                .bike(booking.getBike())
                .customer(booking.getCustomer())
                .rating(request.rating())
                .comment(request.comment())
                .build();

        Review saved = reviewRepository.save(review);
        log.info("Review submitted: booking={}, bike={}, rating={}", bookingId, booking.getBike().getId(), request.rating());
        return toResponse(saved);
    }

    /** Public review listing for a bike's detail page — previously write-only. */
    @Transactional(readOnly = true)
    public Page<BikeReviewResponse> listReviewsForBike(Long bikeId, int page, int size) {
        return reviewRepository.findByBikeIdOrderByCreatedAtDesc(bikeId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toBikeReviewResponse);
    }

    private BikeReviewResponse toBikeReviewResponse(Review r) {
        return new BikeReviewResponse(
                r.getId(),
                firstNameAndInitial(r.getCustomer().getFullName()),
                r.getRating(),
                r.getComment(),
                r.getCreatedAt()
        );
    }

    // Shows "Arun K." instead of the full name, for a little privacy on a public listing.
    private String firstNameAndInitial(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Puduvandi rider";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private ReviewResponse toResponse(Review r) {
        return new ReviewResponse(
                r.getId(),
                r.getBooking().getId(),
                r.getBike().getId(),
                r.getRating(),
                r.getComment(),
                r.getCreatedAt()
        );
    }
}
