package com.puduvandi.payment.controller;

import com.puduvandi.config.RazorpayConfig;
import com.puduvandi.payment.service.PaymentService;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Razorpay's server-to-server webhook calls — currently only
 * refund.processed / refund.failed, which is how {@link PaymentService}
 * finds out a real deposit refund actually settled (see refundDeposit's
 * REFUND_INITIATED step and handleRefundWebhookEvent). Unauthenticated by
 * JWT (permitAll in SecurityConfig, Razorpay can't obtain a bearer token) —
 * security instead comes from verifying X-Razorpay-Signature against the
 * webhook secret configured in the Razorpay dashboard.
 * <p>
 * Always resolves quickly with a 2xx for anything successfully verified,
 * including event types this handler doesn't act on — Razorpay retries a
 * webhook delivery on non-2xx responses, and there's no reason to make it
 * retry an event we're intentionally ignoring.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Razorpay server-to-server event callbacks")
public class RazorpayWebhookController {

    private final PaymentService paymentService;
    private final RazorpayConfig razorpayConfig;

    @PostMapping
    @Operation(summary = "Razorpay webhook callback (refund.processed / refund.failed)")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        String webhookSecret = razorpayConfig.getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Razorpay webhook received but no webhook-secret is configured — rejecting.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("webhook not configured");
        }
        if (signature == null || signature.isBlank()) {
            log.warn("Razorpay webhook received with no X-Razorpay-Signature header — rejecting.");
            return ResponseEntity.badRequest().body("missing signature");
        }

        boolean valid;
        try {
            valid = Utils.verifyWebhookSignature(rawBody, signature, webhookSecret);
        } catch (RazorpayException ex) {
            log.warn("Razorpay webhook signature verification errored: {}", ex.getMessage());
            return ResponseEntity.badRequest().body("signature verification failed");
        }
        if (!valid) {
            log.warn("Razorpay webhook signature mismatch — possible forged request.");
            return ResponseEntity.badRequest().body("invalid signature");
        }

        JSONObject event;
        try {
            event = new JSONObject(rawBody);
        } catch (JSONException ex) {
            log.warn("Razorpay webhook payload is not valid JSON despite a valid signature: {}", ex.getMessage());
            return ResponseEntity.badRequest().body("invalid payload");
        }

        String eventType = event.optString("event", "");
        if ("refund.processed".equals(eventType) || "refund.failed".equals(eventType)) {
            String refundId = extractRefundId(event);
            if (refundId == null) {
                log.warn("Razorpay {} webhook missing payload.refund.entity.id, ignoring.", eventType);
            } else {
                paymentService.handleRefundWebhookEvent(refundId, "refund.processed".equals(eventType));
            }
        } else {
            log.debug("Razorpay webhook event not handled, ignoring: {}", eventType);
        }

        return ResponseEntity.ok("ok");
    }

    private String extractRefundId(JSONObject event) {
        JSONObject payload = event.optJSONObject("payload");
        JSONObject refund = payload == null ? null : payload.optJSONObject("refund");
        JSONObject entity = refund == null ? null : refund.optJSONObject("entity");
        return entity == null ? null : entity.optString("id", null);
    }
}
