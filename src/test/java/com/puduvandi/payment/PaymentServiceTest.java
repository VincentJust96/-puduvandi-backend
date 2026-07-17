package com.puduvandi.payment;

import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.booking.service.BookingService;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.PaymentStatus;
import com.puduvandi.common.enums.PaymentType;
import com.puduvandi.config.RazorpayConfig;
import com.puduvandi.errorlog.service.ErrorLogService;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.payment.entity.Payment;
import com.puduvandi.payment.repository.PaymentRepository;
import com.puduvandi.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for signature verification, idempotency, the mock-mode guard, and
 * the DEPOSIT/FULL/BALANCE payment-plan rules. Real order creation against
 * Razorpay's API is NOT unit-tested here (would require a live network call)
 * — that's covered by manual/live verification instead, same convention as
 * BookingServiceTransitionTest for createBooking.
 * <p>
 * Signatures are computed for real using the documented Razorpay scheme
 * (HMAC-SHA256 of "orderId|paymentId" with the key secret) rather than mocking
 * Utils.verifyPaymentSignature, so these tests exercise the actual verification path.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingService bookingService;
    @Mock private ErrorLogService errorLogService;

    private RazorpayConfig razorpayConfig;
    private PaymentService paymentService;

    private static final Long CUSTOMER_ID = 1L;
    private static final String KEY_SECRET = "test_secret_key_12345";
    private static final String ORDER_ID = "order_TestOrder123";
    private static final String RAZORPAY_PAYMENT_ID = "pay_TestPayment456";

    private Payment payment;

    @BeforeEach
    void setUp() {
        razorpayConfig = new RazorpayConfig();
        razorpayConfig.setKeyId("rzp_test_fake");
        razorpayConfig.setKeySecret(KEY_SECRET);
        razorpayConfig.setMockEnabled(false);
        razorpayConfig.setPaymentExpiryMinutes(15);

        paymentService = new PaymentService(paymentRepository, bookingRepository, bookingService,
                razorpayConfig, errorLogService);

        payment = Payment.builder()
                .id(500L)
                .customerId(CUSTOMER_ID)
                .amount(new BigDecimal("650.00"))
                .currency("INR")
                .status(PaymentStatus.ORDER_CREATED)
                .type(PaymentType.FULL)
                .razorpayOrderId(ORDER_ID)
                .mock(false)
                .build();
    }

    private String realSignature(String orderId, String paymentId) throws Exception {
        String payload = orderId + "|" + paymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private Booking bookingWithStatus(Long id, BookingStatus status) {
        return bookingWithStatus(id, status, BigDecimal.ZERO);
    }

    private Booking bookingWithStatus(Long id, BookingStatus status, BigDecimal amountPaid) {
        return Booking.builder()
                .id(id)
                .bookingReference("PV-20260717-000" + id)
                .status(status)
                .totalAmount(new BigDecimal("2000.00"))
                .amountPaid(amountPaid)
                .build();
    }

    // ===== createOrder =====

    @Test
    @DisplayName("createOrder: mock mode enabled rejects immediately, no repository calls")
    void createOrder_mockEnabled_throws() {
        razorpayConfig.setMockEnabled(true);

        assertThatThrownBy(() -> paymentService.createOrder(CUSTOMER_ID, List.of(10L), PaymentType.FULL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mock mode");

        verifyNoInteractions(bookingRepository, paymentRepository);
    }

    @Test
    @DisplayName("createOrder: booking not found for this customer throws ResourceNotFoundException")
    void createOrder_bookingNotFound_throws() {
        when(bookingRepository.findAllByIdInAndCustomer_IdAndDeletedFalse(List.of(10L, 20L), CUSTOMER_ID))
                .thenReturn(List.of(bookingWithStatus(10L, BookingStatus.PAYMENT_PENDING)));

        assertThatThrownBy(() -> paymentService.createOrder(CUSTOMER_ID, List.of(10L, 20L), PaymentType.FULL))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: FULL on a booking that isn't PAYMENT_PENDING is rejected")
    void createOrder_fullOnNonPending_throws() {
        when(bookingRepository.findAllByIdInAndCustomer_IdAndDeletedFalse(List.of(10L), CUSTOMER_ID))
                .thenReturn(List.of(bookingWithStatus(10L, BookingStatus.CONFIRMED)));

        assertThatThrownBy(() -> paymentService.createOrder(CUSTOMER_ID, List.of(10L), PaymentType.FULL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: DEPOSIT charges exactly 10% of the booking's total")
    void createOrder_deposit_chargesTenPercent() {
        Booking booking = bookingWithStatus(10L, BookingStatus.PAYMENT_PENDING);
        when(bookingRepository.findAllByIdInAndCustomer_IdAndDeletedFalse(List.of(10L), CUSTOMER_ID))
                .thenReturn(List.of(booking));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        // mock mode is off but no real Razorpay reachable in a unit test — expect the
        // order-creation network call to fail past the point where amount is computed;
        // capture the amount via the saved Payment before that call.
        assertThatThrownBy(() -> paymentService.createOrder(CUSTOMER_ID, List.of(10L), PaymentType.DEPOSIT))
                .isInstanceOf(BusinessException.class);

        verify(paymentRepository, atLeastOnce()).save(argThat(p ->
                p.getAmount().compareTo(new BigDecimal("200.00")) == 0 && p.getType() == PaymentType.DEPOSIT));
    }

    @Test
    @DisplayName("createOrder: BALANCE rejects a booking that isn't CONFIRMED")
    void createOrder_balanceOnPending_throws() {
        when(bookingRepository.findAllByIdInAndCustomer_IdAndDeletedFalse(List.of(10L), CUSTOMER_ID))
                .thenReturn(List.of(bookingWithStatus(10L, BookingStatus.PAYMENT_PENDING)));

        assertThatThrownBy(() -> paymentService.createOrder(CUSTOMER_ID, List.of(10L), PaymentType.BALANCE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible");
    }

    @Test
    @DisplayName("createOrder: BALANCE rejects a booking that's already fully paid")
    void createOrder_balanceAlreadyPaid_throws() {
        Booking fullyPaid = bookingWithStatus(10L, BookingStatus.CONFIRMED, new BigDecimal("2000.00"));
        when(bookingRepository.findAllByIdInAndCustomer_IdAndDeletedFalse(List.of(10L), CUSTOMER_ID))
                .thenReturn(List.of(fullyPaid));

        assertThatThrownBy(() -> paymentService.createOrder(CUSTOMER_ID, List.of(10L), PaymentType.BALANCE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already fully paid");
    }

    // ===== verifyAndCapture =====

    @Test
    @DisplayName("verifyAndCapture: FULL payment settles the booking's entire total")
    void verifyAndCapture_full_settlesEntireTotal() throws Exception {
        payment.setType(PaymentType.FULL);
        Booking booking = bookingWithStatus(10L, BookingStatus.PAYMENT_PENDING);
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findAllByPayment_Id(payment.getId())).thenReturn(List.of(booking));

        String signature = realSignature(ORDER_ID, RAZORPAY_PAYMENT_ID);
        List<Long> confirmed = paymentService.verifyAndCapture(CUSTOMER_ID, ORDER_ID, RAZORPAY_PAYMENT_ID, signature);

        assertThat(confirmed).containsExactly(10L);
        assertThat(booking.getAmountPaid()).isEqualByComparingTo("2000.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(bookingService).confirmBookingsAfterPayment(List.of(10L));
    }

    @Test
    @DisplayName("verifyAndCapture: DEPOSIT settles exactly 10% of the booking's total, leaving a balance")
    void verifyAndCapture_deposit_settlesTenPercentOnly() throws Exception {
        payment.setType(PaymentType.DEPOSIT);
        Booking booking = bookingWithStatus(10L, BookingStatus.PAYMENT_PENDING);
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findAllByPayment_Id(payment.getId())).thenReturn(List.of(booking));

        String signature = realSignature(ORDER_ID, RAZORPAY_PAYMENT_ID);
        paymentService.verifyAndCapture(CUSTOMER_ID, ORDER_ID, RAZORPAY_PAYMENT_ID, signature);

        assertThat(booking.getAmountPaid()).isEqualByComparingTo("200.00"); // 10% of 2000
        BigDecimal balanceDue = booking.getTotalAmount().subtract(booking.getAmountPaid());
        assertThat(balanceDue).isEqualByComparingTo("1800.00");
        verify(bookingService).confirmBookingsAfterPayment(List.of(10L));
    }

    @Test
    @DisplayName("verifyAndCapture: BALANCE clears the remaining amount on an already-CONFIRMED booking")
    void verifyAndCapture_balance_clearsRemainder() throws Exception {
        payment.setType(PaymentType.BALANCE);
        Booking booking = bookingWithStatus(10L, BookingStatus.CONFIRMED, new BigDecimal("200.00"));
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findAllByPayment_Id(payment.getId())).thenReturn(List.of(booking));

        String signature = realSignature(ORDER_ID, RAZORPAY_PAYMENT_ID);
        paymentService.verifyAndCapture(CUSTOMER_ID, ORDER_ID, RAZORPAY_PAYMENT_ID, signature);

        assertThat(booking.getAmountPaid()).isEqualByComparingTo("2000.00");
        // confirmBookingsAfterPayment is still called — it's a no-op for an already-CONFIRMED
        // booking, so this doesn't re-fire the confirmation notification.
        verify(bookingService).confirmBookingsAfterPayment(List.of(10L));
    }

    @Test
    @DisplayName("verifyAndCapture: tampered/invalid signature is rejected and marks payment FAILED")
    void verifyAndCapture_invalidSignature_rejects() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findAllByPayment_Id(payment.getId()))
                .thenReturn(List.of(bookingWithStatus(10L, BookingStatus.PAYMENT_PENDING)));

        assertThatThrownBy(() -> paymentService.verifyAndCapture(
                CUSTOMER_ID, ORDER_ID, RAZORPAY_PAYMENT_ID, "not-a-real-signature"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Payment could not be verified");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(bookingService, never()).confirmBookingsAfterPayment(any());
    }

    @Test
    @DisplayName("verifyAndCapture: duplicate call on an already-PAID payment is idempotent")
    void verifyAndCapture_alreadyPaid_isIdempotent() {
        payment.setStatus(PaymentStatus.PAID);
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findAllByPayment_Id(payment.getId()))
                .thenReturn(List.of(bookingWithStatus(10L, BookingStatus.CONFIRMED)));

        List<Long> result = paymentService.verifyAndCapture(CUSTOMER_ID, ORDER_ID, RAZORPAY_PAYMENT_ID, "irrelevant");

        assertThat(result).containsExactly(10L);
        verify(bookingService, never()).confirmBookingsAfterPayment(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("verifyAndCapture: payment belonging to a different customer is rejected")
    void verifyAndCapture_wrongCustomer_throws() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.verifyAndCapture(999L, ORDER_ID, RAZORPAY_PAYMENT_ID, "irrelevant"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong to you");

        verify(bookingRepository, never()).findAllByPayment_Id(any());
    }
}
