package com.puduvandi.booking;

import com.puduvandi.auth.entity.User;
import com.puduvandi.booking.dto.TrackingResponse;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.booking.service.TrackingService;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DeliveryLegType;
import com.puduvandi.common.enums.DeliveryStatus;
import com.puduvandi.common.enums.DeliveryType;
import com.puduvandi.delivery.entity.DeliveryOrder;
import com.puduvandi.delivery.repository.DeliveryOrderRepository;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.owner.entity.OwnerProfile;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingService Unit Tests")
class TrackingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private DeliveryOrderRepository deliveryOrderRepository;

    private TrackingService trackingService;

    private static final Long BOOKING_ID = 100L;
    private static final Long CUSTOMER_ID = 1L;
    private static final Long OWNER_USER_ID = 2L;
    private static final Long STRANGER_ID = 999L;

    private User customer;
    private OwnerProfile ownerProfile;
    private Booking booking;

    @BeforeEach
    void setUp() {
        trackingService = new TrackingService(bookingRepository, deliveryOrderRepository);
        customer = User.builder().id(CUSTOMER_ID).build();
        ownerProfile = OwnerProfile.builder().id(50L).user(User.builder().id(OWNER_USER_ID).build()).build();
        booking = Booking.builder()
                .id(BOOKING_ID)
                .customer(customer)
                .owner(ownerProfile)
                .deliveryType(DeliveryType.SELF_PICKUP)
                .status(BookingStatus.RIDE_STARTED)
                .currentLatitude(new BigDecimal("12.9700"))
                .currentLongitude(new BigDecimal("77.5900"))
                .locationUpdatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getTracking: booking not found throws ResourceNotFoundException")
    void getTracking_bookingNotFound_shouldThrow() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trackingService.getTracking(BOOKING_ID, CUSTOMER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getTracking: unrelated user (neither customer nor owner) is forbidden")
    void getTracking_unauthorizedViewer_shouldThrow() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> trackingService.getTracking(BOOKING_ID, STRANGER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getTracking: self-pickup RIDE_STARTED returns customer location, not stale")
    void getTracking_selfPickup_rideStarted_returnsCustomerLocation() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        TrackingResponse response = trackingService.getTracking(BOOKING_ID, OWNER_USER_ID);

        assertThat(response.trackingRole()).isEqualTo("CUSTOMER");
        assertThat(response.phaseLabel()).isEqualTo("Ride In Progress");
        assertThat(response.latitude()).isEqualTo(new BigDecimal("12.9700"));
        assertThat(response.stale()).isFalse();
    }

    @Test
    @DisplayName("getTracking: self-pickup with stale (>3 min old) location is flagged stale")
    void getTracking_selfPickup_staleLocation_flagged() {
        booking.setLocationUpdatedAt(LocalDateTime.now().minusMinutes(10));
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        TrackingResponse response = trackingService.getTracking(BOOKING_ID, CUSTOMER_ID);

        assertThat(response.stale()).isTrue();
    }

    @Test
    @DisplayName("getTracking: self-pickup CONFIRMED (not yet started) returns NONE / Pickup Scheduled")
    void getTracking_selfPickup_confirmed_returnsNone() {
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        TrackingResponse response = trackingService.getTracking(BOOKING_ID, CUSTOMER_ID);

        assertThat(response.trackingRole()).isEqualTo("NONE");
        assertThat(response.phaseLabel()).isEqualTo("Pickup Scheduled");
        assertThat(response.latitude()).isNull();
    }

    @Test
    @DisplayName("getTracking: partner-delivery with no DeliveryOrder yet returns NONE / Pickup Scheduled")
    void getTracking_partnerDelivery_noOrderYet_returnsNone() {
        booking.setDeliveryType(DeliveryType.PARTNER_DELIVERY);
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(deliveryOrderRepository.findByBookingIdAndLegType(BOOKING_ID, DeliveryLegType.OUTBOUND)).thenReturn(Optional.empty());

        TrackingResponse response = trackingService.getTracking(BOOKING_ID, CUSTOMER_ID);

        assertThat(response.trackingRole()).isEqualTo("NONE");
        assertThat(response.phaseLabel()).isEqualTo("Pickup Scheduled");
    }

    @Test
    @DisplayName("getTracking: partner-delivery CLAIMED tracks the partner's location")
    void getTracking_partnerDelivery_claimed_tracksPartner() {
        booking.setDeliveryType(DeliveryType.PARTNER_DELIVERY);
        DeliveryOrder order = DeliveryOrder.builder()
                .id(500L)
                .status(DeliveryStatus.CLAIMED)
                .partnerCurrentLatitude(new BigDecimal("13.0000"))
                .partnerCurrentLongitude(new BigDecimal("77.6000"))
                .partnerLocationUpdatedAt(LocalDateTime.now())
                .build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(deliveryOrderRepository.findByBookingIdAndLegType(BOOKING_ID, DeliveryLegType.OUTBOUND)).thenReturn(Optional.of(order));

        TrackingResponse response = trackingService.getTracking(BOOKING_ID, CUSTOMER_ID);

        assertThat(response.trackingRole()).isEqualTo("PARTNER");
        assertThat(response.phaseLabel()).isEqualTo("Partner en route to pickup");
        assertThat(response.latitude()).isEqualTo(new BigDecimal("13.0000"));
    }

    @Test
    @DisplayName("getTracking: partner-delivery DELIVERED with booking RIDE_STARTED tracks customer")
    void getTracking_partnerDelivery_delivered_tracksCustomer() {
        booking.setDeliveryType(DeliveryType.PARTNER_DELIVERY);
        booking.setStatus(BookingStatus.RIDE_STARTED);
        DeliveryOrder order = DeliveryOrder.builder().id(500L).status(DeliveryStatus.DELIVERED).build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(deliveryOrderRepository.findByBookingIdAndLegType(BOOKING_ID, DeliveryLegType.OUTBOUND)).thenReturn(Optional.of(order));

        TrackingResponse response = trackingService.getTracking(BOOKING_ID, CUSTOMER_ID);

        assertThat(response.trackingRole()).isEqualTo("CUSTOMER");
        assertThat(response.phaseLabel()).isEqualTo("Ride In Progress");
    }

    @Test
    @DisplayName("getTracking: partner-delivery RETURN_REQUESTED with return leg PICKED_UP tracks the partner returning to owner")
    void getTracking_returnLeg_pickedUp_tracksPartner() {
        booking.setDeliveryType(DeliveryType.PARTNER_DELIVERY);
        booking.setStatus(BookingStatus.RETURN_REQUESTED);
        DeliveryOrder order = DeliveryOrder.builder().id(501L).legType(DeliveryLegType.RETURN)
                .status(DeliveryStatus.PICKED_UP).build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(deliveryOrderRepository.findByBookingIdAndLegType(BOOKING_ID, DeliveryLegType.RETURN)).thenReturn(Optional.of(order));

        TrackingResponse response = trackingService.getTracking(BOOKING_ID, OWNER_USER_ID);

        assertThat(response.trackingRole()).isEqualTo("PARTNER");
        assertThat(response.phaseLabel()).isEqualTo("Partner returning bike to owner");
    }

    @Test
    @DisplayName("getTracking: partner-delivery RETURN_REQUESTED with return leg DELIVERED returns NONE / Completed")
    void getTracking_returnLeg_delivered_returnsNone() {
        booking.setDeliveryType(DeliveryType.PARTNER_DELIVERY);
        booking.setStatus(BookingStatus.RETURN_REQUESTED);
        DeliveryOrder order = DeliveryOrder.builder().id(501L).legType(DeliveryLegType.RETURN)
                .status(DeliveryStatus.DELIVERED).build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(deliveryOrderRepository.findByBookingIdAndLegType(BOOKING_ID, DeliveryLegType.RETURN)).thenReturn(Optional.of(order));

        TrackingResponse response = trackingService.getTracking(BOOKING_ID, CUSTOMER_ID);

        assertThat(response.trackingRole()).isEqualTo("NONE");
        assertThat(response.phaseLabel()).isEqualTo("Completed");
    }
}
