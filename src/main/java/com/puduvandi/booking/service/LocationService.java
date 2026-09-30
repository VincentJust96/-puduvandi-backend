package com.puduvandi.booking.service;

import com.puduvandi.booking.dto.LocationRequest;
import com.puduvandi.booking.dto.LocationResponse;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.realtime.RealtimeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One-time customer location capture for a booking — no continuous tracking.
 * Customer shares their GPS location once; the owner can view it afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final BookingRepository bookingRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Transactional
    public LocationResponse saveCustomerLocation(Long bookingId, Long customerUserId, LocationRequest request) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!booking.getCustomer().getId().equals(customerUserId)) {
            throw new ForbiddenException("You do not have permission to share location for this booking.");
        }

        booking.setCurrentLatitude(request.latitude());
        booking.setCurrentLongitude(request.longitude());
        booking.setLocationUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        try {
            realtimeEventPublisher.locationUpdated(bookingId,
                    List.of(booking.getOwner().getUser().getId()));
        } catch (Exception ex) {
            log.warn("Failed to publish realtime location update for bookingId={}", bookingId, ex);
        }

        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public LocationResponse getCustomerLocation(Long bookingId, Long requestingUserId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        boolean isCustomer = booking.getCustomer().getId().equals(requestingUserId);
        boolean isOwner = booking.getOwner().getUser().getId().equals(requestingUserId);
        if (!isCustomer && !isOwner) {
            throw new ForbiddenException("You do not have permission to view location for this booking.");
        }

        if (booking.getLocationUpdatedAt() == null) {
            throw new BusinessException("Customer has not shared a location for this booking yet.");
        }

        return toResponse(booking);
    }

    private LocationResponse toResponse(Booking booking) {
        return new LocationResponse(
                booking.getCurrentLatitude(),
                booking.getCurrentLongitude(),
                booking.getLocationUpdatedAt()
        );
    }
}
