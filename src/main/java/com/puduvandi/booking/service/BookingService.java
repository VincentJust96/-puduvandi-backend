package com.puduvandi.booking.service;

import com.puduvandi.bike.entity.Bike;
import com.puduvandi.bike.repository.BikeRepository;
import com.puduvandi.booking.dto.BatchBookingRequest;
import com.puduvandi.booking.dto.BookingResponse;
import com.puduvandi.booking.dto.CancelBookingRequest;
import com.puduvandi.booking.dto.CreateBookingRequest;
import com.puduvandi.booking.dto.PriceEstimateResponse;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.common.enums.BikeStatus;
import com.puduvandi.common.enums.BikeVerificationStatus;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DeliveryType;
import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.DocumentType;
import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.delivery.service.DeliveryService;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.notification.service.BookingConfirmationService;
import com.puduvandi.user.repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the full booking lifecycle:
 * CREATED → CONFIRMED → RESERVED → RIDE_STARTED → RETURN_REQUESTED → COMPLETED
 *
 * Key rules:
 * - No owner approval needed. Booking is instantly CONFIRMED after payment (mocked).
 * - Overlap check prevents double-booking.
 * - Customer can only cancel before RESERVED (before ride starts).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BikeRepository bikeRepository;
    private final UserRepository userRepository;
    private final BookingConfirmationService bookingConfirmationService;
    private final DeliveryService deliveryService;
    private final UserDocumentRepository userDocumentRepository;

    @Value("${puduvandi.commission.default-percentage:20.0}")
    private BigDecimal defaultCommissionPercent;

    // ===== PRICE ESTIMATE =====

    /**
     * Calculates price estimate before the customer confirms booking.
     * Does NOT create any booking record.
     */
    @Transactional(readOnly = true)
    public PriceEstimateResponse estimatePrice(Long bikeId,
                                               LocalDateTime pickupDatetime,
                                               LocalDateTime returnDatetime) {
        Bike bike = findApprovedAvailableBike(bikeId);
        validateDateRange(pickupDatetime, returnDatetime);
        return calculateEstimate(bike, pickupDatetime, returnDatetime);
    }

    // ===== CREATE BOOKING =====

    /**
     * Customer creates an instant booking.
     * Flow: validate → check overlap → calculate price → save → mark bike RESERVED → CONFIRMED
     *
     * In Phase 3 payment is MOCKED (auto-confirmed).
     * Phase 4 will integrate Razorpay.
     */
    @Transactional
    public BookingResponse createBooking(Long customerId, CreateBookingRequest request) {
        return createSingleBooking(customerId, request.bikeId(),
                request.pickupDatetime(), request.returnDatetime(), null,
                parseDeliveryType(request.deliveryType()), request.dropoffLatitude(), request.dropoffLongitude());
    }

    /**
     * Creates one booking per bikeId for a shared trip (cart checkout).
     * All-or-nothing: if any bike fails (overlap, unavailable), the whole batch rolls back.
     *
     * DAY mode: return = pickup + quantity days. HOUR mode: return = pickup + quantity hours.
     * Pickup defaults to 10:00 on startDate, clamped to now+15min for same-day bookings.
     */
    @Transactional
    public List<BookingResponse> createBookingBatch(Long customerId, BatchBookingRequest request) {
        boolean dayMode = "DAY".equalsIgnoreCase(request.rentalMode());

        LocalDateTime pickup = request.startDate().atTime(LocalTime.of(10, 0));
        LocalDateTime minPickup = LocalDateTime.now().plusMinutes(15);
        if (pickup.isBefore(minPickup)) {
            pickup = minPickup;
        }
        LocalDateTime returnDt = dayMode
                ? pickup.plusDays(request.quantity())
                : pickup.plusHours(request.quantity());

        DeliveryType deliveryType = parseDeliveryType(request.deliveryType());

        List<BookingResponse> created = new ArrayList<>();
        for (Long bikeId : request.bikeIds()) {
            created.add(createSingleBooking(customerId, bikeId, pickup, returnDt, request.helmetIncluded(),
                    deliveryType, request.dropoffLatitude(), request.dropoffLongitude()));
        }

        log.info("Batch booking created: customer={}, bikes={}, mode={}, quantity={}",
                customerId, request.bikeIds(), request.rentalMode(), request.quantity());
        return created;
    }

    /**
     * Core booking creation shared by single and batch flows.
     * helmetOverride: customer's explicit choice; null = copy from bike.
     * deliveryType: null defaults to SELF_PICKUP. For PARTNER_DELIVERY, dropoffLat/dropoffLng
     * are required and the bike must already have a stored pickup location.
     */
    private BookingResponse createSingleBooking(Long customerId, Long bikeId,
                                                LocalDateTime pickupDatetime,
                                                LocalDateTime returnDatetime,
                                                Boolean helmetOverride,
                                                DeliveryType deliveryType,
                                                BigDecimal dropoffLat,
                                                BigDecimal dropoffLng) {
        validateDateRange(pickupDatetime, returnDatetime);
        DeliveryType resolvedDeliveryType = deliveryType != null ? deliveryType : DeliveryType.SELF_PICKUP;

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", customerId));

        if (!userDocumentRepository.existsByUserIdAndDocumentTypeAndStatusAndDeletedFalse(
                customerId, DocumentType.DRIVING_LICENSE, DocumentStatus.APPROVED)) {
            throw new BusinessException(
                    "Your driving licence must be verified before you can book a bike. "
                    + "Please upload it on your profile page and wait for admin approval.");
        }

        Bike bike = findApprovedAvailableBike(bikeId);

        if (resolvedDeliveryType == DeliveryType.PARTNER_DELIVERY) {
            if (bike.getLatitude() == null || bike.getLongitude() == null) {
                throw new BusinessException(
                        "This bike's pickup location hasn't been set by the owner yet — choose self-pickup instead.");
            }
            if (dropoffLat == null || dropoffLng == null) {
                throw new BusinessException("A drop-off location is required for partner delivery.");
            }
        }

        // Check no overlapping booking exists for this bike
        boolean hasOverlap = bookingRepository.existsOverlappingBooking(
                bike.getId(), pickupDatetime, returnDatetime);
        if (hasOverlap) {
            throw new BusinessException(
                    "Bike " + bike.getBrand() + " " + bike.getModel()
                    + " is already booked for the selected dates. Please choose different dates.");
        }

        // Calculate amounts
        BigDecimal[] amounts = calculateAmounts(bike, pickupDatetime, returnDatetime);
        BigDecimal totalHours      = amounts[0];
        BigDecimal totalDays       = amounts[1];
        BigDecimal baseAmount      = amounts[2];
        BigDecimal commissionAmt   = amounts[3];
        BigDecimal ownerEarning    = amounts[4];
        BigDecimal totalAmount     = baseAmount.add(bike.getSecurityDeposit());

        // Save booking
        Booking booking = Booking.builder()
                .bookingReference(generateBookingReference())
                .customer(customer)
                .bike(bike)
                .owner(bike.getOwner())
                .pickupDatetime(pickupDatetime)
                .returnDatetime(returnDatetime)
                .totalHours(totalHours)
                .totalDays(totalDays)
                .baseAmount(baseAmount)
                .securityDeposit(bike.getSecurityDeposit())
                .totalAmount(totalAmount)
                .commissionPercent(defaultCommissionPercent)
                .commissionAmount(commissionAmt)
                .ownerEarning(ownerEarning)
                .helmetIncluded(helmetOverride != null ? helmetOverride : bike.isHelmetIncluded())
                .status(BookingStatus.CONFIRMED)   // Auto-confirmed (payment mocked)
                .deleted(false)
                .deliveryType(resolvedDeliveryType)
                .dropoffLatitude(dropoffLat)
                .dropoffLongitude(dropoffLng)
                .build();

        bookingRepository.save(booking);

        // Mark bike as RESERVED
        bike.setStatus(BikeStatus.RESERVED);
        bikeRepository.save(bike);

        if (resolvedDeliveryType == DeliveryType.PARTNER_DELIVERY) {
            deliveryService.createDeliveryOrder(booking, bike, dropoffLat, dropoffLng);
        }

        log.info("Booking created: ref={}, customer={}, bike={}, amount={}",
                booking.getBookingReference(), customerId, bike.getId(), totalAmount);

        bookingConfirmationService.sendBookingConfirmation(booking);

        return toResponse(booking);
    }

    // ===== STATUS TRANSITIONS =====

    /**
     * Customer or system marks ride as started.
     */
    @Transactional
    public BookingResponse startRide(Long customerId, Long bookingId) {
        Booking booking = findCustomerBooking(customerId, bookingId);
        assertStatus(booking, BookingStatus.CONFIRMED, "Ride can only be started on CONFIRMED bookings.");

        booking.setStatus(BookingStatus.RIDE_STARTED);
        bookingRepository.save(booking);

        log.info("Ride started: bookingId={}", bookingId);
        return toResponse(booking);
    }

    /**
     * Customer requests to return the bike.
     */
    @Transactional
    public BookingResponse requestReturn(Long customerId, Long bookingId) {
        Booking booking = findCustomerBooking(customerId, bookingId);
        assertStatus(booking, BookingStatus.RIDE_STARTED, "Return can only be requested after ride starts.");

        booking.setStatus(BookingStatus.RETURN_REQUESTED);
        bookingRepository.save(booking);

        log.info("Return requested: bookingId={}", bookingId);
        return toResponse(booking);
    }

    /**
     * System/Admin completes the booking after physical return.
     * Releases bike back to AVAILABLE.
     */
    @Transactional
    public BookingResponse completeBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        assertStatus(booking, BookingStatus.RETURN_REQUESTED, "Booking must be in RETURN_REQUESTED to complete.");

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setActualReturnDatetime(LocalDateTime.now());
        bookingRepository.save(booking);

        // Release bike
        Bike bike = booking.getBike();
        bike.setStatus(BikeStatus.AVAILABLE);
        bikeRepository.save(bike);

        log.info("Booking completed: bookingId={}, bike released to AVAILABLE", bookingId);
        bookingConfirmationService.sendRideCompletionNotification(booking);
        return toResponse(booking);
    }

    /**
     * Customer cancels booking. Only allowed before RIDE_STARTED.
     */
    @Transactional
    public BookingResponse cancelBooking(Long customerId, Long bookingId, CancelBookingRequest request) {
        Booking booking = findCustomerBooking(customerId, bookingId);

        if (booking.getStatus() == BookingStatus.RIDE_STARTED
                || booking.getStatus() == BookingStatus.RETURN_REQUESTED
                || booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException("Booking cannot be cancelled in status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(request.reason());
        bookingRepository.save(booking);

        // Release bike back to AVAILABLE
        Bike bike = booking.getBike();
        if (bike.getStatus() == BikeStatus.RESERVED) {
            bike.setStatus(BikeStatus.AVAILABLE);
            bikeRepository.save(bike);
        }

        if (booking.getDeliveryType() == DeliveryType.PARTNER_DELIVERY) {
            deliveryService.cancelDeliveryForBooking(bookingId);
        }

        log.info("Booking cancelled: bookingId={}, reason={}", bookingId, request.reason());
        return toResponse(booking);
    }

    // ===== QUERY METHODS =====

    @Transactional(readOnly = true)
    public BookingResponse getBookingByReference(String reference) {
        return bookingRepository.findByBookingReferenceAndDeletedFalse(reference)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Booking with reference " + reference + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(Long customerId, int page, int size) {
        return bookingRepository.findByCustomerIdAndDeletedFalse(
                customerId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getOwnerBookings(Long userId, int page, int size) {
        return bookingRepository.findByOwner_UserIdAndDeletedFalse(
                userId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id) {
        return bookingRepository.findByIdAndDeletedFalse(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
    }

    /**
     * Owner marks a RETURN_REQUESTED booking as COMPLETED and releases the bike.
     */
    @Transactional
    public BookingResponse completeBookingAsOwner(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!booking.getOwner().getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to complete this booking.");
        }
        assertStatus(booking, BookingStatus.RETURN_REQUESTED,
                "Booking must be in RETURN_REQUESTED status to complete.");

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setActualReturnDatetime(LocalDateTime.now());
        bookingRepository.save(booking);

        Bike bike = booking.getBike();
        bike.setStatus(BikeStatus.AVAILABLE);
        bikeRepository.save(bike);

        log.info("Booking completed by owner: bookingId={}, ownerUserId={}", bookingId, userId);
        bookingConfirmationService.sendRideCompletionNotification(booking);
        return toResponse(booking);
    }

    // ===== PRIVATE HELPERS =====

    private Bike findApprovedAvailableBike(Long bikeId) {
        Bike bike = bikeRepository.findByIdAndDeletedFalseAndVerificationStatus(
                bikeId, BikeVerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Bike", bikeId));

        if (bike.getStatus() != BikeStatus.AVAILABLE) {
            throw new BusinessException("This bike is currently not available for booking.");
        }
        return bike;
    }

    private void validateDateRange(LocalDateTime pickup, LocalDateTime returnDt) {
        if (!returnDt.isAfter(pickup)) {
            throw new BusinessException("Return datetime must be after pickup datetime.");
        }
        long minutes = ChronoUnit.MINUTES.between(pickup, returnDt);
        if (minutes < 60) {
            throw new BusinessException("Minimum booking duration is 1 hour.");
        }
    }

    /**
     * Calculates: totalHours, totalDays, baseAmount, commissionAmount, ownerEarning
     *
     * Pricing logic:
     * - If duration >= 24 hours → use pricePerDay
     * - If duration < 24 hours → use pricePerHour
     */
    private BigDecimal[] calculateAmounts(Bike bike,
                                          LocalDateTime pickup,
                                          LocalDateTime returnDt) {
        long totalMinutes = ChronoUnit.MINUTES.between(pickup, returnDt);
        BigDecimal totalHours = BigDecimal.valueOf(totalMinutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal totalDays  = BigDecimal.valueOf(totalMinutes)
                .divide(BigDecimal.valueOf(1440), 2, RoundingMode.HALF_UP);

        BigDecimal baseAmount;
        if (totalMinutes >= 1440) {
            // >= 1 day: charge by day
            BigDecimal days = BigDecimal.valueOf(totalMinutes)
                    .divide(BigDecimal.valueOf(1440), 0, RoundingMode.CEILING);
            baseAmount = bike.getPricePerDay().multiply(days);
        } else {
            // < 1 day: charge by hour
            BigDecimal hours = BigDecimal.valueOf(totalMinutes)
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.CEILING);
            baseAmount = bike.getPricePerHour().multiply(hours);
        }

        BigDecimal commissionAmount = baseAmount
                .multiply(defaultCommissionPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal ownerEarning = baseAmount.subtract(commissionAmount);

        return new BigDecimal[]{totalHours, totalDays, baseAmount, commissionAmount, ownerEarning};
    }

    private PriceEstimateResponse calculateEstimate(Bike bike,
                                                    LocalDateTime pickup,
                                                    LocalDateTime returnDt) {
        BigDecimal[] amounts = calculateAmounts(bike, pickup, returnDt);
        BigDecimal totalAmount = amounts[2].add(bike.getSecurityDeposit());
        return new PriceEstimateResponse(
                bike.getId(), bike.getBrand(), bike.getModel(),
                bike.getPricePerHour(), bike.getPricePerDay(),
                amounts[0], amounts[1], amounts[2],
                bike.getSecurityDeposit(), totalAmount,
                defaultCommissionPercent, amounts[3], amounts[4]
        );
    }

    private Booking findCustomerBooking(Long customerId, Long bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (!booking.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException("You do not have permission to access this booking.");
        }
        return booking;
    }

    private void assertStatus(Booking booking, BookingStatus required, String message) {
        if (booking.getStatus() != required) {
            throw new BusinessException(message + " Current status: " + booking.getStatus());
        }
    }

    private DeliveryType parseDeliveryType(String deliveryType) {
        return deliveryType != null ? DeliveryType.valueOf(deliveryType) : null;
    }

    private String generateBookingReference() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "PV-" + date + "-" + String.format("%04d", bookingRepository.nextBookingReferenceNumber());
    }

    private BookingResponse toResponse(Booking b) {
        boolean dayMode = b.getTotalDays().compareTo(BigDecimal.ONE) >= 0;
        int quantity = dayMode
                ? b.getTotalDays().setScale(0, RoundingMode.CEILING).intValue()
                : b.getTotalHours().setScale(0, RoundingMode.CEILING).intValue();
        return new BookingResponse(
                b.getId(),
                b.getBookingReference(),
                b.getCustomer().getId(),
                b.getCustomer().getFullName(),
                b.getBike().getId(),
                b.getBike().getBrand(),
                b.getBike().getModel(),
                b.getBike().getRegistrationNumber(),
                b.getPickupDatetime(),
                b.getReturnDatetime(),
                b.getActualReturnDatetime(),
                b.getTotalHours(),
                b.getTotalDays(),
                b.getBaseAmount(),
                b.getSecurityDeposit(),
                b.getTotalAmount(),
                b.getCommissionPercent(),
                b.getCommissionAmount(),
                b.getOwnerEarning(),
                b.getStatus(),
                b.isHelmetIncluded(),
                b.getCancellationReason(),
                b.getCreatedAt(),
                dayMode ? "DAY" : "HOUR",
                quantity,
                b.getDeliveryType()
        );
    }
}
