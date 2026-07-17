package com.puduvandi.handover;

import com.puduvandi.auth.entity.User;
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
import com.puduvandi.handover.service.HandoverOtpService;
import com.puduvandi.owner.entity.OwnerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HandoverOtpService Unit Tests")
class HandoverOtpServiceTest {

    @Mock private HandoverOtpRepository handoverOtpRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private DeliveryOrderRepository deliveryOrderRepository;
    @Mock private BookingService bookingService;
    @Mock private DeliveryService deliveryService;

    private HandoverOtpService handoverOtpService;

    private static final Long BOOKING_ID = 100L;
    private static final Long CUSTOMER_ID = 1L;
    private static final Long OWNER_USER_ID = 2L;
    private static final Long PARTNER_ID = 3L;

    private User customer;
    private User ownerUser;
    private User partner;
    private OwnerProfile ownerProfile;
    private Booking booking;

    @BeforeEach
    void setUp() {
        handoverOtpService = new HandoverOtpService(
                handoverOtpRepository, bookingRepository, deliveryOrderRepository, bookingService, deliveryService);

        customer = User.builder().id(CUSTOMER_ID).phoneNumber("9000000001").build();
        ownerUser = User.builder().id(OWNER_USER_ID).phoneNumber("9000000002").build();
        partner = User.builder().id(PARTNER_ID).phoneNumber("9000000003").build();
        ownerProfile = OwnerProfile.builder().id(50L).user(ownerUser).build();

        booking = Booking.builder()
                .id(BOOKING_ID)
                .customer(customer)
                .owner(ownerProfile)
                .deliveryType(DeliveryType.SELF_PICKUP)
                .status(BookingStatus.CONFIRMED)
                .build();
    }

    // ===== generate() — PICKUP_SELF =====

    @Test
    @DisplayName("generate PICKUP_SELF: customer generating on CONFIRMED self-pickup booking succeeds")
    void generate_pickupSelf_happyPath() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(handoverOtpRepository.findByBookingIdAndPurposeAndUsedFalse(BOOKING_ID, HandoverPurpose.PICKUP_SELF))
                .thenReturn(Collections.emptyList());
        when(handoverOtpRepository.save(any(HandoverOtp.class))).thenAnswer(inv -> inv.getArgument(0));

        HandoverOtpResponse response = handoverOtpService.generate(BOOKING_ID, HandoverPurpose.PICKUP_SELF, CUSTOMER_ID);

        assertThat(response.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(response.purpose()).isEqualTo(HandoverPurpose.PICKUP_SELF);
        assertThat(response.otp()).matches("\\d{6}");
        verify(handoverOtpRepository).save(any(HandoverOtp.class));
    }

    @Test
    @DisplayName("generate PICKUP_SELF: wrong requester (not the customer) is rejected")
    void generate_pickupSelf_wrongRequester_shouldThrow() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> handoverOtpService.generate(BOOKING_ID, HandoverPurpose.PICKUP_SELF, OWNER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not authorised");

        verify(handoverOtpRepository, never()).save(any());
    }

    @Test
    @DisplayName("generate PICKUP_SELF: booking not in CONFIRMED state is rejected")
    void generate_pickupSelf_wrongState_shouldThrow() {
        booking.setStatus(BookingStatus.RIDE_STARTED);
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> handoverOtpService.generate(BOOKING_ID, HandoverPurpose.PICKUP_SELF, CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CONFIRMED");

        verify(handoverOtpRepository, never()).save(any());
    }

    @Test
    @DisplayName("generate PICKUP_SELF: wrong delivery type (partner-delivery booking) is rejected")
    void generate_pickupSelf_wrongDeliveryType_shouldThrow() {
        booking.setDeliveryType(DeliveryType.PARTNER_DELIVERY);
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> handoverOtpService.generate(BOOKING_ID, HandoverPurpose.PICKUP_SELF, CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not applicable");
    }

    @Test
    @DisplayName("generate: unknown booking throws ResourceNotFoundException")
    void generate_bookingNotFound_shouldThrow() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handoverOtpService.generate(BOOKING_ID, HandoverPurpose.PICKUP_SELF, CUSTOMER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("generate: prior unused OTPs for the same booking+purpose are invalidated")
    void generate_invalidatesPriorUnusedOtps() {
        HandoverOtp priorOtp = HandoverOtp.builder().id(1L).bookingId(BOOKING_ID)
                .purpose(HandoverPurpose.PICKUP_SELF).otpCode("111111").used(false).build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(handoverOtpRepository.findByBookingIdAndPurposeAndUsedFalse(BOOKING_ID, HandoverPurpose.PICKUP_SELF))
                .thenReturn(List.of(priorOtp));
        when(handoverOtpRepository.save(any(HandoverOtp.class))).thenAnswer(inv -> inv.getArgument(0));

        handoverOtpService.generate(BOOKING_ID, HandoverPurpose.PICKUP_SELF, CUSTOMER_ID);

        assertThat(priorOtp.isUsed()).isTrue();
        verify(handoverOtpRepository, times(2)).save(any(HandoverOtp.class)); // prior invalidated + new one
    }

    @Test
    @DisplayName("generate PICKUP_PARTNER: no partner has claimed the delivery yet is rejected")
    void generate_pickupPartner_noClaimedPartner_shouldThrow() {
        booking.setDeliveryType(DeliveryType.PARTNER_DELIVERY);
        DeliveryOrder order = DeliveryOrder.builder().id(500L).booking(booking).partner(null).build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(deliveryOrderRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> handoverOtpService.generate(BOOKING_ID, HandoverPurpose.PICKUP_PARTNER, PARTNER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No delivery partner");
    }

    // ===== verify() =====

    @Test
    @DisplayName("verify PICKUP_SELF: correct OTP transitions booking to RIDE_STARTED")
    void verify_pickupSelf_correctOtp_shouldTransition() {
        HandoverOtp otp = HandoverOtp.builder().id(9L).bookingId(BOOKING_ID)
                .purpose(HandoverPurpose.PICKUP_SELF).otpCode("482913")
                .expiresAt(LocalDateTime.now().plusMinutes(5)).used(false).failedAttempts(0).build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(handoverOtpRepository.findLatestActive(eq(BOOKING_ID), eq(HandoverPurpose.PICKUP_SELF), any()))
                .thenReturn(Optional.of(otp));

        HandoverVerifyResponse response = handoverOtpService.verify(
                BOOKING_ID, HandoverPurpose.PICKUP_SELF, "482913", OWNER_USER_ID);

        assertThat(response.verified()).isTrue();
        assertThat(otp.isUsed()).isTrue();
        assertThat(otp.getVerifiedByUserId()).isEqualTo(OWNER_USER_ID);
        verify(bookingService).transitionToRideStarted(BOOKING_ID);
        verify(deliveryService, never()).transitionToPickedUp(anyLong());
    }

    @Test
    @DisplayName("verify: wrong validator identity is rejected before the OTP is even checked")
    void verify_wrongValidator_shouldThrow() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> handoverOtpService.verify(BOOKING_ID, HandoverPurpose.PICKUP_SELF, "482913", CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not authorised");

        verify(handoverOtpRepository, never()).findLatestActive(any(), any(), any());
        verify(bookingService, never()).transitionToRideStarted(anyLong());
    }

    @Test
    @DisplayName("verify: no active OTP (never generated or expired) is rejected")
    void verify_noActiveOtp_shouldThrow() {
        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(handoverOtpRepository.findLatestActive(eq(BOOKING_ID), eq(HandoverPurpose.PICKUP_SELF), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handoverOtpService.verify(BOOKING_ID, HandoverPurpose.PICKUP_SELF, "482913", OWNER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No active OTP");

        verify(bookingService, never()).transitionToRideStarted(anyLong());
    }

    @Test
    @DisplayName("verify: wrong OTP increments failedAttempts and reports remaining attempts")
    void verify_wrongOtp_shouldIncrementFailedAttempts() {
        HandoverOtp otp = HandoverOtp.builder().id(9L).bookingId(BOOKING_ID)
                .purpose(HandoverPurpose.PICKUP_SELF).otpCode("482913")
                .expiresAt(LocalDateTime.now().plusMinutes(5)).used(false).failedAttempts(0).build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(handoverOtpRepository.findLatestActive(eq(BOOKING_ID), eq(HandoverPurpose.PICKUP_SELF), any()))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> handoverOtpService.verify(BOOKING_ID, HandoverPurpose.PICKUP_SELF, "000000", OWNER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("4 attempt(s) remaining");

        assertThat(otp.getFailedAttempts()).isEqualTo(1);
        assertThat(otp.isUsed()).isFalse();
        verify(bookingService, never()).transitionToRideStarted(anyLong());
    }

    @Test
    @DisplayName("verify: 5th wrong attempt locks out the OTP and forces regeneration")
    void verify_maxFailedAttempts_shouldLockOut() {
        HandoverOtp otp = HandoverOtp.builder().id(9L).bookingId(BOOKING_ID)
                .purpose(HandoverPurpose.PICKUP_SELF).otpCode("482913")
                .expiresAt(LocalDateTime.now().plusMinutes(5)).used(false).failedAttempts(4).build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(handoverOtpRepository.findLatestActive(eq(BOOKING_ID), eq(HandoverPurpose.PICKUP_SELF), any()))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> handoverOtpService.verify(BOOKING_ID, HandoverPurpose.PICKUP_SELF, "000000", OWNER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Maximum attempts exceeded");

        assertThat(otp.getFailedAttempts()).isEqualTo(5);
        assertThat(otp.isUsed()).isTrue(); // invalidated on lockout
    }

    @Test
    @DisplayName("verify RETURN_FINAL: correct OTP transitions both delivery order and booking")
    void verify_returnFinal_shouldTransitionBoth() {
        booking.setDeliveryType(DeliveryType.PARTNER_DELIVERY);
        booking.setStatus(BookingStatus.RETURN_REQUESTED);
        DeliveryOrder order = DeliveryOrder.builder().id(500L).booking(booking).partner(partner).build();
        HandoverOtp otp = HandoverOtp.builder().id(9L).bookingId(BOOKING_ID)
                .purpose(HandoverPurpose.RETURN_FINAL).otpCode("482913")
                .expiresAt(LocalDateTime.now().plusMinutes(5)).used(false).failedAttempts(0).build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(deliveryOrderRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(order));
        when(handoverOtpRepository.findLatestActive(eq(BOOKING_ID), eq(HandoverPurpose.RETURN_FINAL), any()))
                .thenReturn(Optional.of(otp));

        HandoverVerifyResponse response = handoverOtpService.verify(
                BOOKING_ID, HandoverPurpose.RETURN_FINAL, "482913", PARTNER_ID);

        assertThat(response.verified()).isTrue();
        verify(deliveryService).transitionToReturnCompleted(500L);
        verify(bookingService).completeBooking(BOOKING_ID);
    }

    @Test
    @DisplayName("verify RETURN_TO_PARTNER: validator must be the claimed partner, not the owner")
    void verify_returnToPartner_wrongValidator_shouldThrow() {
        booking.setDeliveryType(DeliveryType.PARTNER_DELIVERY);
        booking.setStatus(BookingStatus.RETURN_REQUESTED);
        DeliveryOrder order = DeliveryOrder.builder().id(500L).booking(booking).partner(partner).build();

        when(bookingRepository.findByIdAndDeletedFalse(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(deliveryOrderRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> handoverOtpService.verify(
                BOOKING_ID, HandoverPurpose.RETURN_TO_PARTNER, "482913", OWNER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not authorised");
    }
}
