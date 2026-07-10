package com.puduvandi.delivery.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.bike.entity.Bike;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.common.enums.DeliveryStatus;
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
        DeliveryOrder order = findDelivery(deliveryId);
        if (order.getStatus() != DeliveryStatus.PENDING) {
            throw new BusinessException("This delivery has already been claimed by another partner.");
        }
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", partnerId));

        order.setPartner(partner);
        order.setStatus(DeliveryStatus.CLAIMED);
        order.setClaimedAt(LocalDateTime.now());
        deliveryOrderRepository.save(order);

        return toPartnerResponse(order);
    }

    @Transactional
    public PartnerDeliveryResponse markPickedUp(Long partnerId, Long deliveryId) {
        DeliveryOrder order = findOwnedDelivery(partnerId, deliveryId);
        if (order.getStatus() != DeliveryStatus.CLAIMED) {
            throw new BusinessException("Delivery must be claimed before it can be marked picked up.");
        }
        order.setStatus(DeliveryStatus.PICKED_UP);
        order.setPickedUpAt(LocalDateTime.now());
        deliveryOrderRepository.save(order);

        return toPartnerResponse(order);
    }

    @Transactional
    public PartnerDeliveryResponse markDelivered(Long partnerId, Long deliveryId) {
        DeliveryOrder order = findOwnedDelivery(partnerId, deliveryId);
        if (order.getStatus() != DeliveryStatus.PICKED_UP) {
            throw new BusinessException("Bike must be picked up before it can be marked delivered.");
        }
        order.setStatus(DeliveryStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        deliveryOrderRepository.save(order);

        return toPartnerResponse(order);
    }

    @Transactional(readOnly = true)
    public List<PartnerDeliveryResponse> getMyDeliveries(Long partnerId) {
        return deliveryOrderRepository.findByPartnerIdOrderByCreatedAtDesc(partnerId)
                .stream().map(this::toPartnerResponse).toList();
    }

    // ===== Private helpers =====

    private DeliveryOrder findDelivery(Long deliveryId) {
        return deliveryOrderRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryOrder", deliveryId));
    }

    private DeliveryOrder findOwnedDelivery(Long partnerId, Long deliveryId) {
        DeliveryOrder order = findDelivery(deliveryId);
        if (order.getPartner() == null || !order.getPartner().getId().equals(partnerId)) {
            throw new ForbiddenException("You do not have permission to update this delivery.");
        }
        return order;
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

    private PartnerDeliveryResponse toPartnerResponse(DeliveryOrder order) {
        Booking booking = order.getBooking();
        Bike bike = booking.getBike();
        User customer = booking.getCustomer();

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
                customer.getFullName(),
                customer.getPhoneNumber(),
                order.getCreatedAt(),
                order.getClaimedAt(),
                order.getPickedUpAt(),
                order.getDeliveredAt()
        );
    }
}
