package com.puduvandi.payment.service;

import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.booking.service.BookingService;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DepositStatus;
import com.puduvandi.common.enums.PaymentStatus;
import com.puduvandi.common.enums.PaymentType;
import com.puduvandi.config.RazorpayConfig;
import com.puduvandi.errorlog.service.ErrorLogService;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.payment.dto.PaymentOrderResponse;
import com.puduvandi.payment.entity.Payment;
import com.puduvandi.payment.repository.PaymentRepository;
import com.puduvandi.push.service.WebPushService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
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
 * Creates Razorpay orders for a booking's payment (DEPOSIT/FULL at booking
 * time, or BALANCE to clear a remaining deposit), and verifies the signature
 * Razorpay returns after the customer pays. Never talks to Razorpay when
 * mock-enabled=true.
 * <p>
 * DEPOSIT is 10% of a booking's totalAmount — the rest is due before pickup
 * (see HandoverOtpService, which blocks pickup OTP generation until
 * booking.amountPaid reaches totalAmount). FULL clears it all upfront.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final BigDecimal DEPOSIT_FRACTION = new BigDecimal("0.10");

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final RazorpayConfig razorpayConfig;
    private final ErrorLogService errorLogService;
    private final WebPushService webPushService;

    /**
     * Creates (or re-creates, for a retry) a Payment covering all given bookings and asks
     * Razorpay for an order.
     * <p>
     * DEPOSIT/FULL: all bookings must belong to the caller and be PAYMENT_PENDING (the initial
     * payment moment right after booking creation).
     * BALANCE: all bookings must be CONFIRMED with a remaining balance (amountPaid &lt; totalAmount)
     * — this is how a customer clears the rest of a DEPOSIT-plan booking before pickup.
     */
    @Transactional
    public PaymentOrderResponse createOrder(Long customerId, List<Long> bookingIds, PaymentType type) {
        if (razorpayConfig.isMockEnabled()) {
            throw new BusinessException(
                    "Payments are running in mock mode — bookings are already confirmed on creation.");
        }

        List<Booking> bookings = bookingRepository.findAllByIdInAndCustomer_IdAndDeletedFalse(bookingIds, customerId);
        if (bookings.size() != bookingIds.size()) {
            throw new ResourceNotFoundException("One or more bookings were not found for this customer.");
        }

        BigDecimal totalAmount = switch (type) {
            case DEPOSIT -> {
                requireStatus(bookings, BookingStatus.PAYMENT_PENDING);
                yield bookings.stream()
                        .map(b -> b.getTotalAmount().multiply(DEPOSIT_FRACTION).setScale(2, RoundingMode.HALF_UP))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            case FULL -> {
                requireStatus(bookings, BookingStatus.PAYMENT_PENDING);
                yield bookings.stream()
                        .map(b -> b.getTotalAmount().subtract(b.getAmountPaid()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            case BALANCE -> {
                requireStatus(bookings, BookingStatus.CONFIRMED);
                for (Booking booking : bookings) {
                    if (booking.getAmountPaid().compareTo(booking.getTotalAmount()) >= 0) {
                        throw new BusinessException(
                                "Booking " + booking.getBookingReference() + " is already fully paid.");
                    }
                }
                yield bookings.stream()
                        .map(b -> b.getTotalAmount().subtract(b.getAmountPaid()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        };

        Payment payment = paymentRepository.save(Payment.builder()
                .customerId(customerId)
                .amount(totalAmount)
                .currency("INR")
                .status(PaymentStatus.CREATED)
                .type(type)
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

            log.info("Razorpay order created: paymentId={}, razorpayOrderId={}, type={}, amount={}",
                    payment.getId(), razorpayOrderId, type, totalAmount);

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

    private void requireStatus(List<Booking> bookings, BookingStatus required) {
        for (Booking booking : bookings) {
            if (booking.getStatus() != required) {
                throw new BusinessException(
                        "Booking " + booking.getBookingReference() + " is not eligible for this payment (status: "
                                + booking.getStatus() + ", expected " + required + ").");
            }
        }
    }

    /**
     * Verifies the signature Razorpay returned after checkout, applies the paid amount to
     * every booking tied to that payment, and confirms any still-PAYMENT_PENDING ones.
     * Idempotent — a duplicate verify call for an already-PAID payment just returns the
     * current booking IDs without reprocessing anything.
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

        // DEPOSIT settles each booking's own 10% (not a proportional split of the aggregate
        // payment) — recomputed from each booking's own totalAmount so multi-bike trips are
        // exact even if amounts don't divide evenly. FULL/BALANCE both fully settle the booking.
        for (Booking booking : bookings) {
            BigDecimal newAmountPaid = switch (payment.getType()) {
                case DEPOSIT -> booking.getTotalAmount().multiply(DEPOSIT_FRACTION).setScale(2, RoundingMode.HALF_UP);
                case FULL, BALANCE -> booking.getTotalAmount();
            };
            booking.setAmountPaid(newAmountPaid);
        }
        bookingRepository.saveAll(bookings);

        // No-ops for bookings already CONFIRMED (the BALANCE case) — only flips bookings still
        // PAYMENT_PENDING (the DEPOSIT/FULL case), which is exactly what's needed here.
        bookingService.confirmBookingsAfterPayment(bookingIds);

        log.info("Payment verified and captured: paymentId={}, razorpayOrderId={}, type={}, bookings={}",
                payment.getId(), razorpayOrderId, payment.getType(), bookingIds);
        return bookingIds;
    }

    /**
     * Resolves a completed booking's security deposit — called by DepositClaimService
     * (claim approved/rejected) and DepositReleaseTask (auto-release after the grace
     * period). Never throws past the caller: both callers process bookings in a batch
     * and one Razorpay failure shouldn't abort the rest — a failure is recorded as
     * REFUND_FAILED (visible to admin) rather than silently retried or swallowed.
     * <p>
     * Refunds against whatever {@code booking.getPayment()} currently points to (the
     * payment that most recently settled the booking) — see the plan's "known scoping
     * trade-off" note: this is always correct for the FULL plan and correct in the
     * overwhelming majority of DEPOSIT-plan cases, since there's no history table
     * linking a booking to every payment it ever had.
     */
    @Transactional
    public void refundDeposit(Booking booking, BigDecimal refundAmount) {
        if (refundAmount.compareTo(BigDecimal.ZERO) == 0) {
            markRefunded(booking, BigDecimal.ZERO);
            log.info("Deposit forfeited (no refund): bookingId={}", booking.getId());
            return;
        }

        Payment payment = booking.getPayment();
        if (payment == null || razorpayConfig.isMockEnabled()) {
            markRefunded(booking, refundAmount);
            log.info("Deposit refund simulated (mock/no real payment): bookingId={}, amount={}",
                    booking.getId(), refundAmount);
            return;
        }

        long amountInPaise = refundAmount.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

        try {
            RazorpayClient client = new RazorpayClient(razorpayConfig.getKeyId(), razorpayConfig.getKeySecret());
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", amountInPaise);
            Refund refund = client.payments.refund(payment.getRazorpayPaymentId(), refundRequest);

            markRefunded(booking, refundAmount);
            log.info("Deposit refunded: bookingId={}, razorpayRefundId={}, amount={}",
                    booking.getId(), refund.get("id").toString(), refundAmount);
        } catch (RazorpayException ex) {
            booking.setDepositStatus(DepositStatus.REFUND_FAILED);
            // Doubles as "amount attempted" on failure (vs. "amount actually
            // refunded" on success) so retryFailedRefund() knows what to retry
            // without needing a separate column.
            booking.setDepositRefundAmount(refundAmount);
            bookingRepository.save(booking);
            errorLogService.logServiceError(ex, "Booking", booking.getId(), booking.getCustomer().getId());
            log.error("Deposit refund failed: bookingId={}, amount={}", booking.getId(), refundAmount, ex);
        }
    }

    private void markRefunded(Booking booking, BigDecimal refundAmount) {
        booking.setDepositStatus(DepositStatus.REFUNDED);
        booking.setDepositRefundAmount(refundAmount);
        booking.setDepositRefundedAt(LocalDateTime.now());
        bookingRepository.save(booking);
    }

    /**
     * Auto-refunds any completed booking's deposit that's still HELD (no claim
     * filed against it) once the grace period has passed — a customer's money
     * doesn't sit held indefinitely just because no one followed up. Called by
     * DepositReleaseTask.
     */
    @Transactional
    public void releaseUnclaimedDeposits(int graceHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(graceHours);
        List<Booking> eligible = bookingRepository.findAllByStatusAndDepositStatusAndActualReturnDatetimeBefore(
                BookingStatus.COMPLETED, DepositStatus.HELD, cutoff);

        if (eligible.isEmpty()) {
            return;
        }
        log.info("Auto-releasing {} unclaimed deposit(s)", eligible.size());
        for (Booking booking : eligible) {
            refundDeposit(booking, booking.getSecurityDeposit());
            try {
                webPushService.sendToUser(booking.getCustomer().getId(), "Deposit resolved",
                        "Your deposit of ₹" + booking.getSecurityDeposit() + " has been refunded.", "/bookings");
            } catch (Exception ex) {
                log.warn("Failed to push deposit-auto-released notification for bookingId={}", booking.getId(), ex);
            }
        }
    }

    /**
     * Finds PAYMENT_PENDING bookings past the configured expiry window and cancels them,
     * releasing the bike. Anchored on the booking's own age (not the payment's) — this also
     * covers a customer who created a booking and never even started a Razorpay order at all,
     * not just one whose order/verification stalled. Called by PaymentExpiryTask.
     * <p>
     * Only ever touches PAYMENT_PENDING bookings, so a DEPOSIT-plan booking that's already
     * CONFIRMED (with a balance still due) is never affected by this sweep — the balance
     * simply stays due until paid or the customer's ride proceeds regardless.
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
