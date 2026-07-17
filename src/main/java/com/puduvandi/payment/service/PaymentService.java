package com.puduvandi.payment.service;

import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.booking.service.BookingService;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.PaymentStatus;
import com.puduvandi.config.RazorpayConfig;
import com.puduvandi.errorlog.service.ErrorLogService;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.payment.dto.PaymentOrderResponse;
import com.puduvandi.payment.entity.Payment;
import com.puduvandi.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Creates Razorpay orders for one or more PAYMENT_PENDING bookings from the
 * same checkout trip, and verifies the signature Razorpay returns after the
 * customer pays. Never talks to Razorpay when mock-enabled=true.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final RazorpayConfig razorpayConfig;
    private final ErrorLogService errorLogService;

    /**
     * Creates (or re-creates, for a retry) a Payment covering all given bookings and asks
     * Razorpay for an order. All bookings must belong to the caller and be PAYMENT_PENDING —
     * this is also how a customer resumes payment on a booking they abandoned earlier,
     * since re-calling this just supersedes the stale payment_id link with a fresh attempt.
     */
    @Transactional
    public PaymentOrderResponse createOrder(Long customerId, List<Long> bookingIds) {
        if (razorpayConfig.isMockEnabled()) {
            throw new BusinessException(
                    "Payments are running in mock mode — bookings are already confirmed on creation.");
        }

        List<Booking> bookings = bookingRepository.findAllByIdInAndCustomer_IdAndDeletedFalse(bookingIds, customerId);
        if (bookings.size() != bookingIds.size()) {
            throw new ResourceNotFoundException("One or more bookings were not found for this customer.");
        }
        for (Booking booking : bookings) {
            if (booking.getStatus() != BookingStatus.PAYMENT_PENDING) {
                throw new BusinessException(
                        "Booking " + booking.getBookingReference() + " is not awaiting payment (status: "
                                + booking.getStatus() + ").");
            }
        }

        BigDecimal totalAmount = bookings.stream()
                .map(Booking::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Payment payment = paymentRepository.save(Payment.builder()
                .customerId(customerId)
                .amount(totalAmount)
                .currency("INR")
                .status(PaymentStatus.CREATED)
                .mock(false)
                .build());

        for (Booking booking : bookings) {
            booking.setPayment(payment);
        }
        bookingRepository.saveAll(bookings);

        long amountInPaise = totalAmount.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

        try {
            RazorpayClient client = new RazorpayClient(razorpayConfig.getKeyId(), razorpayConfig.getKeySecret());
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", payment.getCurrency());
            orderRequest.put("receipt", "payment_" + payment.getId());
            Order order = client.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            payment.setRazorpayOrderId(razorpayOrderId);
            payment.setStatus(PaymentStatus.ORDER_CREATED);
            paymentRepository.save(payment);

            log.info("Razorpay order created: paymentId={}, razorpayOrderId={}, amount={}",
                    payment.getId(), razorpayOrderId, totalAmount);

            return new PaymentOrderResponse(payment.getId(), razorpayOrderId, razorpayConfig.getKeyId(),
                    totalAmount, amountInPaise, payment.getCurrency());
        } catch (RazorpayException ex) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Order creation failed: " + ex.getMessage());
            paymentRepository.save(payment);
            errorLogService.logServiceError(ex, "Payment", payment.getId(), customerId);
            throw new BusinessException("Could not start payment right now. Please try again.");
        }
    }

    /**
     * Verifies the signature Razorpay returned after checkout, then confirms every booking
     * tied to that payment. Idempotent — a duplicate verify call for an already-PAID payment
     * just returns the current booking IDs without reprocessing anything.
     */
    @Transactional
    public List<Long> verifyAndCapture(Long customerId, String razorpayOrderId,
                                        String razorpayPaymentId, String razorpaySignature) {
        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for this order."));

        if (!payment.getCustomerId().equals(customerId)) {
            throw new BusinessException("This payment does not belong to you.");
        }

        List<Booking> bookings = bookingRepository.findAllByPayment_Id(payment.getId());
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();

        if (payment.getStatus() == PaymentStatus.PAID) {
            return bookingIds; // already processed — nothing more to do
        }
        if (payment.getStatus() != PaymentStatus.ORDER_CREATED) {
            throw new BusinessException("This payment is not awaiting verification (status: " + payment.getStatus() + ").");
        }

        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", razorpayOrderId);
        options.put("razorpay_payment_id", razorpayPaymentId);
        options.put("razorpay_signature", razorpaySignature);

        boolean valid;
        try {
            valid = Utils.verifyPaymentSignature(options, razorpayConfig.getKeySecret());
        } catch (RazorpayException ex) {
            errorLogService.logServiceError(ex, "Payment", payment.getId(), customerId);
            throw new BusinessException("Payment verification failed. Please contact support if you were charged.");
        }

        if (!valid) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Signature verification failed");
            paymentRepository.save(payment);
            log.warn("Razorpay signature verification failed: paymentId={}, razorpayOrderId={}", payment.getId(), razorpayOrderId);
            throw new BusinessException("Payment could not be verified.");
        }

        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setRazorpaySignature(razorpaySignature);
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);

        bookingService.confirmBookingsAfterPayment(bookingIds);

        log.info("Payment verified and captured: paymentId={}, razorpayOrderId={}, bookings={}",
                payment.getId(), razorpayOrderId, bookingIds);
        return bookingIds;
    }

    /**
     * Finds PAYMENT_PENDING bookings past the configured expiry window and cancels them,
     * releasing the bike. Anchored on the booking's own age (not the payment's) — this also
     * covers a customer who created a booking and never even started a Razorpay order at all,
     * not just one whose order/verification stalled. Called by PaymentExpiryTask.
     */
    @Transactional
    public void expireStalePendingBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(razorpayConfig.getPaymentExpiryMinutes());
        List<Booking> stale = bookingRepository.findAllByStatusAndCreatedAtBefore(BookingStatus.PAYMENT_PENDING, cutoff);

        if (stale.isEmpty()) {
            return;
        }
        log.info("Expiring {} stale unpaid booking(s)", stale.size());

        for (Booking booking : stale) {
            Payment payment = booking.getPayment();
            if (payment != null && payment.getStatus() != PaymentStatus.PAID) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Payment window expired");
                paymentRepository.save(payment);
            }
            bookingService.expireUnpaidBooking(booking.getId());
        }
    }
}
