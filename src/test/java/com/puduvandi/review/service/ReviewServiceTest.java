package com.puduvandi.review.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.bike.entity.Bike;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService Unit Tests")
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private BookingRepository bookingRepository;

    private ReviewService reviewService;

    private static final Long CUSTOMER_ID = 1L;
    private static final Long BOOKING_ID = 10L;
    private static final Long BIKE_ID = 20L;

    private User customer;
    private Bike bike;
    private Booking booking;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, bookingRepository);
        customer = User.builder().id(CUSTOMER_ID).fullName("Arun Kumar").build();
        bike = Bike.builder().id(BIKE_ID).brand("Honda").model("Activa").build();
        booking = Booking.builder().id(BOOKING_ID).customer(customer).bike(bike)
                .status(BookingStatus.COMPLETED).build();
    }

    @Test
    @DisplayName("submitReview: booking not found throws ResourceNotFoundException")
    void submitReview_bookingNotFound_throws() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.submitReview(CUSTOMER_ID, BOOKING_ID, new SubmitReviewRequest(5, "Great!")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("submitReview: a different customer's booking is forbidden")
    void submitReview_notOwnBooking_throwsForbidden() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> reviewService.submitReview(999L, BOOKING_ID, new SubmitReviewRequest(5, "Great!")))
                .isInstanceOf(ForbiddenException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitReview: booking not COMPLETED is rejected")
    void submitReview_notCompleted_throws() {
        booking.setStatus(BookingStatus.RIDE_STARTED);
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> reviewService.submitReview(CUSTOMER_ID, BOOKING_ID, new SubmitReviewRequest(5, "Great!")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("completed");
    }

    @Test
    @DisplayName("submitReview: booking already reviewed is rejected with a 409-mapped ConflictException")
    void submitReview_alreadyReviewed_throwsConflict() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(BOOKING_ID)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.submitReview(CUSTOMER_ID, BOOKING_ID, new SubmitReviewRequest(5, "Great!")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already rated");
    }

    @Test
    @DisplayName("submitReview: valid request saves the review")
    void submitReview_valid_savesReview() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(BOOKING_ID)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = reviewService.submitReview(CUSTOMER_ID, BOOKING_ID, new SubmitReviewRequest(4, "Smooth ride"));

        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.comment()).isEqualTo("Smooth ride");
        assertThat(response.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(response.bikeId()).isEqualTo(BIKE_ID);
    }

    @Test
    @DisplayName("listReviewsForBike: maps customer full name to a shortened public display name")
    void listReviewsForBike_shortensCustomerName() {
        Review review = Review.builder().id(1L).booking(booking).bike(bike).customer(customer)
                .rating(5).comment("Loved it").build();
        when(reviewRepository.findByBikeIdOrderByCreatedAtDesc(eq(BIKE_ID), any()))
                .thenReturn(new PageImpl<>(List.of(review)));

        Page<BikeReviewResponse> result = reviewService.listReviewsForBike(BIKE_ID, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).customerName()).isEqualTo("Arun K.");
        assertThat(result.getContent().get(0).rating()).isEqualTo(5);
    }
}
