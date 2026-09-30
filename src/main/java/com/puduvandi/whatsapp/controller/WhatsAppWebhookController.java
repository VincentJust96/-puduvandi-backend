package com.puduvandi.whatsapp.controller;

import com.puduvandi.whatsapp.config.WhatsAppProperties;
import com.puduvandi.whatsapp.service.WebhookSignatureVerifier;
import com.puduvandi.whatsapp.service.WhatsAppInboundProcessor;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Meta's WhatsApp webhook. Public (no JWT) — authenticity comes from the
 * verify token on the GET handshake and the HMAC signature on every POST.
 * Only exists when puduvandi.whatsapp.enabled=true.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/api/v1/whatsapp/webhook")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "puduvandi.whatsapp.enabled", havingValue = "true")
public class WhatsAppWebhookController {

    private final WhatsAppProperties properties;
    private final WebhookSignatureVerifier signatureVerifier;
    private final WhatsAppInboundProcessor processor;

    /** One-time handshake when the webhook URL is saved in Meta's dashboard. */
    @GetMapping
    public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
                                         @RequestParam("hub.verify_token") String token,
                                         @RequestParam("hub.challenge") String challenge) {
        String expected = properties.getVerifyToken();
        boolean ok = "subscribe".equals(mode) && expected != null && !expected.isBlank()
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            log.warn("WhatsApp webhook verification rejected");
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(challenge);
    }

    /** Incoming customer messages. Always 200 for a valid signature — Meta retries anything else. */
    @PostMapping
    public ResponseEntity<String> receive(@RequestBody byte[] body,
                                          @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        String raw = new String(body, StandardCharsets.UTF_8);
        if (!signatureVerifier.isValid(raw, signature)) {
            log.warn("WhatsApp webhook rejected: bad or missing signature");
            return ResponseEntity.status(403).build();
        }
        processor.process(raw);
        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}
