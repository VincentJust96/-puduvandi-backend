package com.puduvandi.delivery.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.bike.entity.Bike;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.common.enums.DeliveryStatus;
import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.delivery.dto.DeliveryResponse;
import com.puduvandi.delivery.dto.PartnerDeliveryResponse;
import com.puduvandi.delivery.entity.DeliveryOrder;
import com.puduvandi.delivery.repository.DeliveryOrderRepository;
import com.puduvandi.delivery.repository.DeliverySettingsRepository;
import com.puduvandi.delivery.util.GeoUtils;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One-time-claim delivery jobs: a partner brings the bike from its stored
 * pickup location to the customer's chosen drop-off point, priced per km.
 * No auto-matching — first partner to claim a PENDING job gets it.
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final DeliverySettingsRepository deliverySettingsRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createDeliveryOrder(Booking booking, Bike bike, BigDecimal dropoffLat, BigDecimal dropoffLng) {
        BigDecimal rate = getActiveRate();
        BigDecimal distanceKm = GeoUtils.haversineKm(bike.getLatitude(), bike.getLongitude(), dropoffLat, dropoffLng);
        BigDecimal fee = distanceKm.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        DeliveryOrder order = DeliveryOrder.builder()
                .booking(booking)
                .pickupLatitude(bike.getLatitude())
                .pickupLongitude(bike.getLongitude())
                .dropoffLatitude(dropoffLat)
                .dropoffLongitude(dropoffLng)
                .distanceKm(distanceKm)
                .deliveryFee(fee)
                .status(DeliveryStatus.PENDING)
                .build();

        deliveryOrderRepository.save(order);
    }

    @Transactional
    public void cancelDeliveryForBooking(Long bookingId) {
        deliveryOrderRepository.findByBookingId(bookingId).ifPresent(order -> {
            if (order.getStatus() == DeliveryStatus.PENDING || order.getStatus() == DeliveryStatus.CLAIMED) {
                order.setStatus(DeliveryStatus.CANCELLED);
                deliveryOrderRepository.save(order);
            }
        });
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getDeliveryForBooking(Long bookingId, Long requestingUserId) {
        DeliveryOrder order = deliveryOrderRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryOrder for booking", bookingId));

        Booking booking = order.getBooking();
        boolean isCustomer = booking.getCustomer().getId().equals(requestingUserId);
        boolean isOwner = booking.getOwner().getUser().getId().equals(requestingUserId);
        if (!isCustomer && !isOwner) {
            throw new ForbiddenException("You do not have permission to view this delivery.");
        }

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public BigDecimal getActiveRate() {
        return deliverySettingsRepository.findTopByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new ResourceNotFoundException("DeliverySettings", 1L))
                .getRatePerKm();
    }

    @Transactional(readOnly = true)
    public List<PartnerDeliveryResponse> listAvailable() {
        return deliveryOrderRepository.findByStatusOrderByCreatedAtAsc(DeliveryStatus.PENDING)
                .stream().map(this::toPartnerResponse).toList();
    }

    @Transactional
    public PartnerDeliveryResponse claim(Long partnerId, Long deliveryId) {
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", partnerId));
        if (partner.getKycStatus() != KycStatus.APPROVED) {
            throw new BusinessException(
                    "Your delivery partner profile is pending admin approval. "
                    + "You cannot claim deliveries until it is approved.");
        }

        // Row-level lock FIRST, then re-check status — closes the TOCTOU race where two
        // partners both read PENDING before either claim() commits.
        DeliveryOrder order = deliveryOrderRepository.lockById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryOrder", deliveryId));
        if (order.getStatus() != DeliveryStatus.PENDING) {
            throw new BusinessException("This delivery has already been claimed by another partner.");
        }

        order.setPartner(partner);
        order.setStatus(DeliveryStatus.CLAIMED);
        order.setClaimedAt(LocalDateTime.now());
        deliveryOrderRepository.save(order);

        return toPartnerResponse(order);
    }

    // ===== OTP-gated handover transitions =====
    // Raw status transitions with no caller-identity check of their own — only reachable
    // via the OTP-gated handover flow (HandoverOtpService), which has already verified the
    // requester's role+identity against the booking/delivery order before calling these.

    /** PICKUP_PARTNER: partner has picked the bike up from the owner. CLAIMED → PICKED_UP. */
    @Transactional
    public PartnerDeliveryResponse transitionToPickedUp(Long deliveryId) {
        DeliveryOrder order = findDelivery(deliveryId);
        if (order.getStatus() != DeliveryStatus.CLAIMED) {
            throw new BusinessException("Delivery must be claimed before it can be marked picked up.");
        }
        order.setStatus(DeliveryStatus.PICKED_UP);
        order.setPickedUpAt(LocalDateTime.now());
        deliveryOrderRepository.save(order);

        return toPartnerResponse(order);
    }

    /** RECEIVE_PARTNER: partner has delivered the bike to the customer. PICKED_UP → DELIVERED. */
    @Transactional
    public PartnerDeliveryResponse transitionToDelivered(Long deliveryId) {
        DeliveryOrder order = findDelivery(deliveryId);
        if (order.getStatus() != DeliveryStatus.PICKED_UP) {
            throw new BusinessException("Bike must be picked up before it can be marked delivered.");
        }
        order.setStatus(DeliveryStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        deliveryOrderRepository.save(order);

        return toPartnerResponse(order);
    }

    /** RETURN_TO_PARTNER: customer has handed the bike back to the partner. DELIVERED → RETURN_COLLECTED. */
    @Transactional
    public PartnerDeliveryResponse transitionToReturnCollected(Long deliveryId) {
        DeliveryOrder order = findDelivery(deliveryId);
        if (order.getStatus() != DeliveryStatus.DELIVERED) {
            throw new BusinessException("Bike must have been delivered before a return can be collected.");
        }
        order.setStatus(DeliveryStatus.RETURN_COLLECTED);
        order.setReturnCollectedAt(LocalDateTime.now());
        deliveryOrderRepository.save(order);

        return toPartnerResponse(order);
    }

    /** RETURN_FINAL: partner has handed the returned bike back to the owner. RETURN_COLLECTED → RETURN_COMPLETED. */
    @Transactional
    public PartnerDeliveryResponse transitionToReturnCompleted(Long deliveryId) {
        DeliveryOrder order = findDelivery(deliveryId);
        if (order.getStatus() != DeliveryStatus.RETURN_COLLECTED) {
            throw new BusinessException("Bike must be collected from the customer before the return can be completed.");
        }
        order.setStatus(DeliveryStatus.RETURN_COMPLETED);
        order.setReturnCompletedAt(LocalDateTime.now());
        deliveryOrderRepository.save(order);

        return toPartnerResponse(order);
    }

    @Transactional(readOnly = true)
    public List<PartnerDeliveryResponse> getMyDeliveries(Long partnerId) {
        return deliveryOrderRepository.findByPartnerIdOrderByCreatedAtDesc(partnerId)
                .stream().map(this::toPartnerResponse).toList();
    }

    /**
     * Partner pushes their own current GPS location while actively moving bike
     * (owner→partner or partner→customer leg, either direction). Only allowed
     * while the order is in a state where the partner is physically in transit.
     */
    @Transactional
    public void updatePartnerLocation(Long partnerId, Long deliveryId, BigDecimal latitude, BigDecimal longitude) {
        DeliveryOrder order = findDelivery(deliveryId);
        if (order.getPartner() == null || !order.getPartner().getId().equals(partnerId)) {
            throw new ForbiddenException("You do not have permission to update location for this delivery.");
        }
        if (order.getStatus() != DeliveryStatus.CLAIMED
                && order.getStatus() != DeliveryStatus.PICKED_UP
                && order.getStatus() != DeliveryStatus.RETURN_COLLECTED) {
            throw new BusinessException("Location updates are only accepted while a delivery leg is in progress.");
        }
        order.setPartnerCurrentLatitude(latitude);
        order.setPartnerCurrentLongitude(longitude);
        order.setPartnerLocationUpdatedAt(LocalDateTime.now());
        deliveryOrderRepository.save(order);
    }

    // ===== Private helpers =====

    private DeliveryOrder findDelivery(Long deliveryId) {
        return deliveryOrderRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryOrder", deliveryId));
    }

    private DeliveryResponse toResponse(DeliveryOrder order) {
        User partner = order.getPartner();
        return new DeliveryResponse(
                order.getId(),
                order.getStatus(),
                order.getDistanceKm(),
                order.getDeliveryFee(),
                partner != null ? partner.getFullName() : null,
                partner != null ? partner.getPhoneNumber() : null,
                order.getClaimedAt(),
                order.getPickedUpAt(),
                order.getDeliveredAt()
        );
    }

    /**
     * Customer contact is only surfaced to the partner once the bike has actually been
     * picked up from the owner — before that, the partner has no legitimate need for it
     * and it would leak PII while the job is still just a claimed/unfulfilled listing.
     */
    private static final java.util.Set<DeliveryStatus> CUSTOMER_CONTACT_VISIBLE_STATUSES = java.util.Set.of(
            DeliveryStatus.PICKED_UP, DeliveryStatus.DELIVERED,
            DeliveryStatus.RETURN_COLLECTED, DeliveryStatus.RETURN_COMPLETED);

    private PartnerDeliveryResponse toPartnerResponse(DeliveryOrder order) {
        Booking booking = order.getBooking();
        Bike bike = booking.getBike();
        User customer = booking.getCustomer();
        boolean contactVisible = CUSTOMER_CONTACT_VISIBLE_STATUSES.contains(order.getStatus());

        return new PartnerDeliveryResponse(
                order.getId(),
                booking.getId(),
                booking.getBookingReference(),
                bike.getBrand(),
                bike.getModel(),
                order.getPickupLatitude(),
                order.getPickupLongitude(),
                order.getDropoffLatitude(),
                order.getDropoffLongitude(),
                order.getDistanceKm(),
                order.getDeliveryFee(),
                order.getStatus(),
                contactVisible ? customer.getFullName() : null,
                contactVisible ? customer.getPhoneNumber() : null,
                order.getCreatedAt(),
                order.getClaimedAt(),
                order.getPickedUpAt(),
                order.getDeliveredAt()
        );
    }
}
