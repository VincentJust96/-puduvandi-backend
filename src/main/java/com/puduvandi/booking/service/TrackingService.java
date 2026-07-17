package com.puduvandi.booking.service;

import com.puduvandi.booking.dto.TrackingResponse;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DeliveryStatus;
import com.puduvandi.common.enums.DeliveryType;
import com.puduvandi.delivery.entity.DeliveryOrder;
import com.puduvandi.delivery.repository.DeliveryOrderRepository;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Resolves, at read time, which actor (customer or delivery partner) the
 * owner should currently be tracking for a booking, and returns their last
 * known polled location. No push/WebSocket layer — both the customer
 * ({@code POST /bookings/{id}/customer-location}) and the partner
 * ({@code POST /partner/deliveries/{id}/location}) push periodically while
 * this endpoint is polled by whoever is watching.
 * <p>
 * The tracked actor switches automatically as the booking/delivery order
 * progress through their handover legs — see resolve() for the full mapping.
 */
@Service
@RequiredArgsConstructor
public class TrackingService {

    private static final long STALE_AFTER_MINUTES = 3;

    private final BookingRepository bookingRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;

    @Transactional(readOnly = true)
    public TrackingResponse getTracking(Long bookingId, Long requestingUserId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        boolean isCustomer = booking.getCustomer().getId().equals(requestingUserId);
        boolean isOwner = booking.getOwner().getUser().getId().equals(requestingUserId);
        if (!isCustomer && !isOwner) {
            throw new ForbiddenException("You do not have permission to view tracking for this booking.");
        }

        return booking.getDeliveryType() == DeliveryType.PARTNER_DELIVERY
                ? resolvePartnerDelivery(booking)
                : resolveSelfPickup(booking);
    }

    private TrackingResponse resolveSelfPickup(Booking booking) {
        return switch (booking.getStatus()) {
            case RIDE_STARTED -> fromCustomer(booking, "Ride In Progress");
            case RETURN_REQUESTED -> none("Return Scheduled");
            case COMPLETED -> none("Completed");
            case CANCELLED -> none("Cancelled");
            default -> none("Pickup Scheduled");
        };
    }

    private TrackingResponse resolvePartnerDelivery(Booking booking) {
        Optional<DeliveryOrder> orderOpt = deliveryOrderRepository.findByBookingId(booking.getId());
        if (orderOpt.isEmpty()) {
            return none("Pickup Scheduled");
        }
        DeliveryOrder order = orderOpt.get();

        return switch (order.getStatus()) {
            case PENDING -> none("Waiting for delivery partner");
            case CLAIMED -> fromPartner(order, "Partner en route to pickup");
            case PICKED_UP -> fromPartner(order, "In Transit to Customer");
            case DELIVERED -> booking.getStatus() == BookingStatus.RETURN_REQUESTED
                    ? fromCustomer(booking, "Return Scheduled")
                    : fromCustomer(booking, "Ride In Progress");
            case RETURN_COLLECTED -> fromPartner(order, "Partner returning to owner");
            case RETURN_COMPLETED -> none("Completed");
            case CANCELLED -> none("Cancelled");
        };
    }

    private TrackingResponse fromCustomer(Booking booking, String phaseLabel) {
        return build("CUSTOMER", phaseLabel,
                booking.getCurrentLatitude(), booking.getCurrentLongitude(), booking.getLocationUpdatedAt());
    }

    private TrackingResponse fromPartner(DeliveryOrder order, String phaseLabel) {
        return build("PARTNER", phaseLabel,
                order.getPartnerCurrentLatitude(), order.getPartnerCurrentLongitude(), order.getPartnerLocationUpdatedAt());
    }

    private TrackingResponse none(String phaseLabel) {
        return new TrackingResponse("NONE", phaseLabel, null, null, null, false);
    }

    private TrackingResponse build(String role, String phaseLabel, BigDecimal lat, BigDecimal lng, LocalDateTime updatedAt) {
        boolean stale = updatedAt == null || updatedAt.isBefore(LocalDateTime.now().minusMinutes(STALE_AFTER_MINUTES));
        return new TrackingResponse(role, phaseLabel, lat, lng, updatedAt, stale);
    }
}
