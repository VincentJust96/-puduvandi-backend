package com.puduvandi.payment;

import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.booking.service.BookingService;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.PaymentStatus;
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
 * Unit tests for signature verification, idempotency, and the mock-mode guard.
 * Real order creation against Razorpay's API is NOT unit-tested here (would require
 * a live network call) — that's covered by manual/live verification instead, same
 * convention as BookingServiceTransitionTest for createBooking.
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

    // ===== createOrder =====

    @Test
    @DisplayName("createOrder: mock mode enabled rejects immediately, no repository calls")
    void createOrder_mockEnabled_throws() {
        razorpayConfig.setMockEnabled(true);

        assertThatThrownBy(() -> paymentService.createOrder(CUSTOMER_ID, List.of(10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mock mode");

        verifyNoInteractions(bookingRepository, paymentRepository);
    }

    @Test
    @DisplayName("createOrder: booking not found for this customer throws ResourceNotFoundException")
    void createOrder_bookingNotFound_throws() {
        when(bookingRepository.findAllByIdInAndCustomer_IdAndDeletedFalse(List.of(10L, 20L), CUSTOMER_ID))
                .thenReturn(List.of(bookingWithStatus(10L, BookingStatus.PAYMENT_PENDING)));

        assertThatThrownBy(() -> paymentService.createOrder(CUSTOMER_ID, List.of(10L, 20L)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: a booking that isn't PAYMENT_PENDING is rejected")
    void createOrder_bookingNotPending_throws() {
        when(bookingRepository.findAllByIdInAndCustomer_IdAndDeletedFalse(List.of(10L), CUSTOMER_ID))
                .thenReturn(List.of(bookingWithStatus(10L, BookingStatus.CONFIRMED)));

        assertThatThrownBy(() -> paymentService.createOrder(CUSTOMER_ID, List.of(10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not awaiting payment");

        verify(paymentRepository, never()).save(any());
    }

    private Booking bookingWithStatus(Long id, BookingStatus status) {
        return Booking.builder()
                .id(id)
                .bookingReference("PV-20260717-000" + id)
                .status(status)
                .totalAmount(new BigDecimal("325.00"))
                .build();
    }

    // ===== verifyAndCapture =====

    @Test
    @DisplayName("verifyAndCapture: valid signature marks payment PAID and confirms bookings")
    void verifyAndCapture_validSignature_confirmsBookings() throws Exception {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findAllByPayment_Id(payment.getId()))
                .thenReturn(List.of(bookingWithStatus(10L, BookingStatus.PAYMENT_PENDING),
                        bookingWithStatus(11L, BookingStatus.PAYMENT_PENDING)));

        String signature = realSignature(ORDER_ID, RAZORPAY_PAYMENT_ID);

        List<Long> confirmed = paymentService.verifyAndCapture(CUSTOMER_ID, ORDER_ID, RAZORPAY_PAYMENT_ID, signature);

        assertThat(confirmed).containsExactlyInAnyOrder(10L, 11L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getRazorpayPaymentId()).isEqualTo(RAZORPAY_PAYMENT_ID);
        verify(bookingService).confirmBookingsAfterPayment(List.of(10L, 11L));
        verify(paymentRepository).save(payment);
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
