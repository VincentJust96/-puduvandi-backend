package com.puduvandi.booking;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.bike.entity.Bike;
import com.puduvandi.bike.repository.BikeRepository;
import com.puduvandi.booking.dto.BookingResponse;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.booking.service.BookingService;
import com.puduvandi.common.enums.BikeStatus;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.delivery.service.DeliveryService;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.notification.service.BookingConfirmationService;
import com.puduvandi.user.repository.UserDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focused tests for the two "raw" state transitions (transitionToRideStarted,
 * completeBooking) that were opened up for HandoverOtpService to call directly
 * (no identity check of their own — the caller is trusted to have already
 * validated role+identity). Only reachability/precondition behavior is tested
 * here, not the full createBooking flow (covered by manual/live verification).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService raw-transition Unit Tests")
class BookingServiceTransitionTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BikeRepository bikeRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingConfirmationService bookingConfirmationService;
    @Mock private DeliveryService deliveryService;
    @Mock private UserDocumentRepository userDocumentRepository;

    private BookingService bookingService;

    private static final Long BOOKING_ID = 100L;

    private Booking booking;
    private Bike bike;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, bikeRepository, userRepository,
                bookingConfirmationService, deliveryService, userDocumentRepository);

        User customer = User.builder().id(1L).fullName("Cust").build();
        bike = Bike.builder().id(10L).brand("Honda").model("Activa").registrationNumber("KA-01-AB-1234")
                .status(BikeStatus.RESERVED).build();

        booking = Booking.builder()
                .id(BOOKING_ID)
                .customer(customer)
                .bike(bike)
                .status(BookingStatus.CONFIRMED)
                .totalHours(new BigDecimal("5"))
                .totalDays(BigDecimal.ZERO)
                .baseAmount(new BigDecimal("100"))
                .securityDeposit(new BigDecimal("50"))
                .totalAmount(new BigDecimal("150"))
                .commissionPercent(new BigDecimal("20"))
                .commissionAmount(new BigDecimal("20"))
                .ownerEarning(new BigDecimal("80"))
                .build();
    }

    // ===== transitionToRideStarted =====

    @Test
    @DisplayName("transitionToRideStarted: CONFIRMED -> RIDE_STARTED succeeds")
    void transitionToRideStarted_fromConfirmed_succeeds() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.transitionToRideStarted(BOOKING_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.RIDE_STARTED);
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("transitionToRideStarted: wrong status (already RIDE_STARTED) is rejected")
    void transitionToRideStarted_wrongStatus_shouldThrow() {
        booking.setStatus(BookingStatus.RIDE_STARTED);
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.transitionToRideStarted(BOOKING_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CONFIRMED");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("transitionToRideStarted: booking not found throws ResourceNotFoundException")
    void transitionToRideStarted_bookingNotFound_shouldThrow() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.transitionToRideStarted(BOOKING_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== completeBooking =====

    @Test
    @DisplayName("completeBooking: RETURN_REQUESTED -> COMPLETED releases the bike to AVAILABLE")
    void completeBooking_fromReturnRequested_releasesBike() {
        booking.setStatus(BookingStatus.RETURN_REQUESTED);
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.completeBooking(BOOKING_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(bike.getStatus()).isEqualTo(BikeStatus.AVAILABLE);
        assertThat(booking.getActualReturnDatetime()).isNotNull();
        verify(bikeRepository).save(bike);
        verify(bookingConfirmationService).sendRideCompletionNotification(booking);
    }

    @Test
    @DisplayName("completeBooking: wrong status (CONFIRMED, not yet RETURN_REQUESTED) is rejected")
    void completeBooking_wrongStatus_shouldThrow() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.completeBooking(BOOKING_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RETURN_REQUESTED");

        verify(bikeRepository, never()).save(any());
    }
}
