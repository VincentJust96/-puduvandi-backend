package com.puduvandi.delivery;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.bike.entity.Bike;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.common.enums.DeliveryLegType;
import com.puduvandi.common.enums.DeliveryStatus;
import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.delivery.dto.PartnerDeliveryResponse;
import com.puduvandi.delivery.entity.DeliveryOrder;
import com.puduvandi.delivery.entity.DeliverySettings;
import com.puduvandi.delivery.repository.DeliveryOrderRepository;
import com.puduvandi.delivery.repository.DeliverySettingsRepository;
import com.puduvandi.delivery.service.DeliveryService;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.partner.repository.PartnerProfileRepository;
import com.puduvandi.push.service.WebPushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryService Unit Tests")
class DeliveryServiceTest {

    @Mock private DeliveryOrderRepository deliveryOrderRepository;
    @Mock private DeliverySettingsRepository deliverySettingsRepository;
    @Mock private UserRepository userRepository;
    @Mock private PartnerProfileRepository partnerProfileRepository;
    @Mock private WebPushService webPushService;

    private DeliveryService deliveryService;

    private static final Long PARTNER_ID = 3L;
    private static final Long DELIVERY_ID = 500L;

    private User partner;
    private Booking booking;
    private Bike bike;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryService(deliveryOrderRepository, deliverySettingsRepository, userRepository,
                partnerProfileRepository, webPushService);

        partner = User.builder().id(PARTNER_ID).kycStatus(KycStatus.APPROVED).build();
        bike = Bike.builder().id(10L).brand("Honda").model("Activa")
                .latitude(new BigDecimal("11.9300")).longitude(new BigDecimal("79.8300")).build();
        User customer = User.builder().id(1L).fullName("Cust Name").phoneNumber("9000000001").build();
        booking = Booking.builder().id(100L).bookingReference("PV-1").bike(bike).customer(customer)
                .dropoffLatitude(new BigDecimal("11.9500")).dropoffLongitude(new BigDecimal("79.8100")).build();
    }

    // ===== claim() =====

    @Test
    @DisplayName("claim: partner with unapproved KYC cannot claim")
    void claim_unapprovedKyc_shouldThrow() {
        partner.setKycStatus(KycStatus.PENDING);
        when(userRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThatThrownBy(() -> deliveryService.claim(PARTNER_ID, DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pending admin approval");

        verify(deliveryOrderRepository, never()).lockById(any());
    }

    @Test
    @DisplayName("claim: unknown partner user throws ResourceNotFoundException")
    void claim_unknownPartner_shouldThrow() {
        when(userRepository.findById(PARTNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.claim(PARTNER_ID, DELIVERY_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("claim: delivery already claimed by another partner is rejected (race-condition guard)")
    void claim_alreadyClaimed_shouldThrow() {
        DeliveryOrder order = DeliveryOrder.builder().id(DELIVERY_ID).booking(booking)
                .status(DeliveryStatus.CLAIMED).build();

        when(userRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(deliveryOrderRepository.lockById(DELIVERY_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.claim(PARTNER_ID, DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been claimed");

        verify(deliveryOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("claim: happy path — PENDING delivery claimed successfully via row lock")
    void claim_pendingDelivery_shouldSucceed() {
        DeliveryOrder order = DeliveryOrder.builder().id(DELIVERY_ID).booking(booking)
                .status(DeliveryStatus.PENDING).build();

        when(userRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(deliveryOrderRepository.lockById(DELIVERY_ID)).thenReturn(Optional.of(order));

        PartnerDeliveryResponse response = deliveryService.claim(PARTNER_ID, DELIVERY_ID);

        assertThat(response.status()).isEqualTo(DeliveryStatus.CLAIMED);
        assertThat(order.getPartner()).isEqualTo(partner);
        verify(deliveryOrderRepository).save(order);
        // Contact should NOT be visible yet at CLAIMED (only from PICKED_UP onward)
        assertThat(response.customerName()).isNull();
        assertThat(response.customerPhone()).isNull();
    }

    // ===== state transitions =====

    @Test
    @DisplayName("transitionToPickedUp: CLAIMED -> PICKED_UP succeeds and reveals customer contact")
    void transitionToPickedUp_fromClaimed_succeeds() {
        DeliveryOrder order = DeliveryOrder.builder().id(DELIVERY_ID).booking(booking)
                .status(DeliveryStatus.CLAIMED).build();
        when(deliveryOrderRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(order));

        PartnerDeliveryResponse response = deliveryService.transitionToPickedUp(DELIVERY_ID);

        assertThat(response.status()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(response.customerName()).isEqualTo("Cust Name");
        assertThat(response.customerPhone()).isEqualTo("9000000001");
    }

    @Test
    @DisplayName("transitionToPickedUp: from wrong status (PENDING) is rejected")
    void transitionToPickedUp_wrongStatus_shouldThrow() {
        DeliveryOrder order = DeliveryOrder.builder().id(DELIVERY_ID).booking(booking)
                .status(DeliveryStatus.PENDING).build();
        when(deliveryOrderRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.transitionToPickedUp(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be claimed");
    }

    @Test
    @DisplayName("transitionToDelivered: from wrong status (CLAIMED) is rejected")
    void transitionToDelivered_wrongStatus_shouldThrow() {
        DeliveryOrder order = DeliveryOrder.builder().id(DELIVERY_ID).booking(booking)
                .status(DeliveryStatus.CLAIMED).build();
        when(deliveryOrderRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.transitionToDelivered(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be picked up");
    }

    // ===== createReturnDeliveryOrder() =====

    @Test
    @DisplayName("createReturnDeliveryOrder: creates a PENDING RETURN-leg order, customer -> bike's base, its own fee")
    void createReturnDeliveryOrder_createsIndependentReturnLeg() {
        DeliverySettings settings = DeliverySettings.builder().ratePerKm(new BigDecimal("15.00")).active(true).build();
        when(deliverySettingsRepository.findTopByActiveTrueOrderByIdDesc()).thenReturn(Optional.of(settings));

        deliveryService.createReturnDeliveryOrder(booking);

        var captor = org.mockito.ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderRepository).save(captor.capture());
        DeliveryOrder saved = captor.getValue();

        assertThat(saved.getLegType()).isEqualTo(DeliveryLegType.RETURN);
        assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(saved.getPartner()).isNull();
        // Pickup is the customer's drop-off point; final drop-off is the bike's own base — reversed vs. the outbound leg.
        assertThat(saved.getPickupLatitude()).isEqualTo(booking.getDropoffLatitude());
        assertThat(saved.getPickupLongitude()).isEqualTo(booking.getDropoffLongitude());
        assertThat(saved.getDropoffLatitude()).isEqualTo(bike.getLatitude());
        assertThat(saved.getDropoffLongitude()).isEqualTo(bike.getLongitude());
        assertThat(saved.getDeliveryFee()).isGreaterThan(BigDecimal.ZERO);
    }

    // ===== leg-aware customer-contact visibility =====

    @Test
    @DisplayName("RETURN leg: customer contact is visible as soon as CLAIMED (customer is the return pickup point)")
    void returnLeg_contactVisible_assoonAsClaimed() {
        DeliveryOrder order = DeliveryOrder.builder().id(DELIVERY_ID).booking(booking)
                .legType(DeliveryLegType.RETURN).status(DeliveryStatus.PENDING).build();

        when(userRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(deliveryOrderRepository.lockById(DELIVERY_ID)).thenReturn(Optional.of(order));

        PartnerDeliveryResponse response = deliveryService.claim(PARTNER_ID, DELIVERY_ID);

        assertThat(response.status()).isEqualTo(DeliveryStatus.CLAIMED);
        assertThat(response.customerName()).isEqualTo("Cust Name");
        assertThat(response.customerPhone()).isEqualTo("9000000001");
    }

    // ===== updatePartnerLocation() =====

    @Test
    @DisplayName("updatePartnerLocation: wrong partner (not assigned to this delivery) is forbidden")
    void updatePartnerLocation_wrongPartner_shouldThrow() {
        DeliveryOrder order = DeliveryOrder.builder().id(DELIVERY_ID).booking(booking)
                .partner(partner).status(DeliveryStatus.PICKED_UP).build();
        when(deliveryOrderRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.updatePartnerLocation(
                999L, DELIVERY_ID, new BigDecimal("12.9"), new BigDecimal("77.5")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updatePartnerLocation: no partner assigned yet (still PENDING) is forbidden")
    void updatePartnerLocation_noPartnerAssigned_shouldThrow() {
        DeliveryOrder order = DeliveryOrder.builder().id(DELIVERY_ID).booking(booking)
                .partner(null).status(DeliveryStatus.PENDING).build();
        when(deliveryOrderRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.updatePartnerLocation(
                PARTNER_ID, DELIVERY_ID, new BigDecimal("12.9"), new BigDecimal("77.5")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updatePartnerLocation: rejected once delivery is DELIVERED (leg no longer in progress)")
    void updatePartnerLocation_afterDelivered_shouldThrow() {
        DeliveryOrder order = DeliveryOrder.builder().id(DELIVERY_ID).booking(booking)
                .partner(partner).status(DeliveryStatus.DELIVERED).build();
        when(deliveryOrderRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.updatePartnerLocation(
                PARTNER_ID, DELIVERY_ID, new BigDecimal("12.9"), new BigDecimal("77.5")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("leg is in progress");
    }

    @Test
    @DisplayName("updatePartnerLocation: accepted while PICKED_UP (assigned partner, in transit)")
    void updatePartnerLocation_pickedUp_succeeds() {
        DeliveryOrder order = DeliveryOrder.builder().id(DELIVERY_ID).booking(booking)
                .partner(partner).status(DeliveryStatus.PICKED_UP).build();
        when(deliveryOrderRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(order));

        deliveryService.updatePartnerLocation(PARTNER_ID, DELIVERY_ID, new BigDecimal("12.90"), new BigDecimal("77.50"));

        assertThat(order.getPartnerCurrentLatitude()).isEqualTo(new BigDecimal("12.90"));
        assertThat(order.getPartnerLocationUpdatedAt()).isNotNull();
        verify(deliveryOrderRepository).save(order);
    }
}
