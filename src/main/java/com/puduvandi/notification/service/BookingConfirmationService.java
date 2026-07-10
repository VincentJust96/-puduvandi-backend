package com.puduvandi.notification.service;

import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.errorlog.service.ErrorLogService;
import com.puduvandi.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * Builds and sends the customer-facing SMS/WhatsApp messages tied to booking
 * lifecycle events. Bookings on this platform are auto-confirmed on creation
 * (no owner approval step — see BookingService), so "confirmation" here means
 * "notify the customer their already-CONFIRMED booking is ready", not a status change.
 * <p>
 * Every public method swallows its own exceptions so a Twilio/DB hiccup here
 * can never fail the booking flow that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingConfirmationService {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final ErrorLogService errorLogService;

    @Value("${puduvandi.support-phone:+91-9999999999}")
    private String supportPhone;

    @Transactional
    public void sendBookingConfirmation(Long bookingId) {
        withBooking(bookingId, this::sendBookingConfirmation);
    }

    public void sendBookingConfirmation(Booking booking) {
        safely(booking, () -> {
            String message = buildConfirmationMessage(booking);
            notificationService.sendBoth(booking.getId(), booking.getCustomer().getPhoneNumber(), message);
            log.info("Booking confirmation notification sent: bookingId={}", booking.getId());
        });
    }

    @Transactional
    public void sendPickupReminder(Long bookingId) {
        withBooking(bookingId, booking -> {
            String message = buildPickupReminderMessage(booking);
            notificationService.sendBoth(booking.getId(), booking.getCustomer().getPhoneNumber(), message);
            log.info("Pickup reminder sent: bookingId={}", booking.getId());
        });
    }

    @Transactional
    public void sendRideCompletionNotification(Long bookingId) {
        withBooking(bookingId, this::sendRideCompletionNotification);
    }

    public void sendRideCompletionNotification(Booking booking) {
        safely(booking, () -> {
            String message = buildCompletionMessage(booking);
            notificationService.sendBoth(booking.getId(), booking.getCustomer().getPhoneNumber(), message);
            log.info("Ride completion notification sent: bookingId={}", booking.getId());
        });
    }

    // ===== Message builders =====

    private String buildConfirmationMessage(Booking booking) {
        return "✅ Booking Confirmed!\n" +
                "Bike: " + bikeLabel(booking) + "\n" +
                "Registration: " + booking.getBike().getRegistrationNumber() + "\n" +
                "Pickup: " + booking.getPickupDatetime().format(DISPLAY_FORMAT) + "\n" +
                "Duration: " + durationLabel(booking) + "\n" +
                "Amount: ₹" + booking.getTotalAmount() + "\n" +
                "Pickup point: " + pickupPointLabel(booking) + "\n" +
                "Track: https://puduvandi.com/track/" + booking.getBookingReference() + "\n" +
                "Contact: " + supportPhone + "\n" +
                "Safe riding! 🏍️";
    }

    private String buildPickupReminderMessage(Booking booking) {
        return "🔔 Pickup Reminder!\n" +
                "Your Puduvandi bike is ready.\n" +
                "Time: " + booking.getPickupDatetime().format(DISPLAY_FORMAT) + "\n" +
                "Location: " + pickupPointLabel(booking) + "\n" +
                "Bike: " + bikeLabel(booking) + "\n" +
                "Confirm: https://puduvandi.com/confirm/" + booking.getBookingReference() + "\n" +
                "Help: " + supportPhone;
    }

    private String buildCompletionMessage(Booking booking) {
        return "🎉 Ride Completed!\n" +
                "Duration: " + durationLabel(booking) + "\n" +
                "Amount Paid: ₹" + booking.getTotalAmount() + "\n" +
                "Rate: https://puduvandi.com/rate/" + booking.getBookingReference() + "\n" +
                "Book again: https://puduvandi.com\n" +
                "Promo: PUDUVANDI10 for 10% off!";
    }

    private String bikeLabel(Booking booking) {
        return booking.getBike().getBrand() + " " + booking.getBike().getModel();
    }

    private String durationLabel(Booking booking) {
        boolean dayMode = booking.getTotalDays().signum() > 0
                && booking.getTotalHours().compareTo(java.math.BigDecimal.valueOf(24)) >= 0;
        return dayMode
                ? booking.getTotalDays() + " day(s)"
                : booking.getTotalHours() + " hour(s)";
    }

    private String pickupPointLabel(Booking booking) {
        String businessName = booking.getOwner().getBusinessName();
        return (businessName == null || businessName.isBlank()) ? "your host" : businessName;
    }

    // ===== Internal =====

    private void withBooking(Long bookingId, java.util.function.Consumer<Booking> action) {
        try {
            Booking booking = bookingRepository.findByIdAndDeletedFalse(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
            action.accept(booking);
        } catch (Exception ex) {
            log.error("Failed to send booking notification: bookingId={}, error={}", bookingId, ex.getMessage());
            errorLogService.logServiceError(ex, "Booking", bookingId, null);
        }
    }

    private void safely(Booking booking, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Failed to send booking notification: bookingId={}, error={}", booking.getId(), ex.getMessage());
            errorLogService.logServiceError(ex, "Booking", booking.getId(), null);
        }
    }
}
