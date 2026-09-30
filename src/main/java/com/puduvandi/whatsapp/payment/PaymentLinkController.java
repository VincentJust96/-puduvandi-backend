package com.puduvandi.whatsapp.payment;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.payment.dto.PaymentOrderResponse;
import com.puduvandi.payment.dto.VerifyPaymentRequest;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * The no-login payment page for WhatsApp customers. Public (no JWT) — the signed,
 * single-use token in the URL is the only credential; see PaymentLinkService.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/pay")
@RequiredArgsConstructor
public class PaymentLinkController {

    private static final Resource PAGE = new ClassPathResource("whatsapp/pay.html");

    private final PaymentLinkService paymentLinkService;

    /** The page itself is static; its script reads the token from the URL and calls the endpoints below. */
    @GetMapping(value = "/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> page(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                // keep the token out of the Referer header sent to Razorpay's script host
                .header("Referrer-Policy", "no-referrer")
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(PAGE);
    }

    @GetMapping("/{token}/details")
    public ResponseEntity<ApiResponse<PaymentLinkDetails>> details(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success("Booking fetched", paymentLinkService.details(token)));
    }

    @PostMapping("/{token}/order")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success("Payment order created", paymentLinkService.createOrder(token)));
    }

    @PostMapping("/{token}/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@PathVariable String token,
                                                    @Valid @RequestBody VerifyPaymentRequest request) {
        paymentLinkService.verify(token, request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature());
        return ResponseEntity.ok(ApiResponse.success("Payment received — your confirmation is on its way on WhatsApp"));
    }
}
