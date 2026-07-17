package com.puduvandi.payment.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.payment.dto.CreatePaymentOrderRequest;
import com.puduvandi.payment.dto.PaymentOrderResponse;
import com.puduvandi.payment.dto.VerifyPaymentRequest;
import com.puduvandi.payment.service.PaymentService;
import com.puduvandi.security.PuduvandiUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Razorpay order creation and verification")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/order")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create a Razorpay order covering one or more PAYMENT_PENDING bookings")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody CreatePaymentOrderRequest request) {

        PaymentOrderResponse order = paymentService.createOrder(principal.getUserId(), request.bookingIds());
        return ResponseEntity.ok(ApiResponse.success("Payment order created", order));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Verify a completed Razorpay payment and confirm its bookings")
    public ResponseEntity<ApiResponse<List<Long>>> verify(
            @AuthenticationPrincipal PuduvandiUserPrincipal principal,
            @Valid @RequestBody VerifyPaymentRequest request) {

        List<Long> confirmedBookingIds = paymentService.verifyAndCapture(
                principal.getUserId(), request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature());
        return ResponseEntity.ok(ApiResponse.success("Payment verified", confirmedBookingIds));
    }
}
