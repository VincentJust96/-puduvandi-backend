package com.puduvandi.whatsapp.controller;

import com.puduvandi.whatsapp.config.WhatsAppProperties;
import com.puduvandi.whatsapp.service.WebhookSignatureVerifier;
import com.puduvandi.whatsapp.service.WhatsAppInboundProcessor;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Meta's WhatsApp webhook. Public (no JWT) — authenticity comes from the
 * verify token on the GET handshake and the HMAC signature on every POST.
 * Only exists when puduvandi.whatsapp.enabled=true, or in dev mode
 * (puduvandi.whatsapp.dev-mode=true), where POSTs need no signature but
 * must come from localhost.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/api/v1/whatsapp/webhook")
@RequiredArgsConstructor
@ConditionalOnExpression("${puduvandi.whatsapp.enabled:false} or ${puduvandi.whatsapp.dev-mode:false}")
public class WhatsAppWebhookController {

    private final WhatsAppProperties properties;
    private final WebhookSignatureVerifier signatureVerifier;
    private final WhatsAppInboundProcessor processor;

    @PostConstruct
    void checkMode() {
        if (!properties.isDevMode()) {
            return;
        }
        if (properties.isEnabled()) {
            // Dev mode drops the signature check — never allow it next to the real channel
            throw new IllegalStateException(
                    "puduvandi.whatsapp.dev-mode and puduvandi.whatsapp.enabled cannot both be true");
        }
        log.warn("WhatsApp DEV MODE: webhook accepts UNSIGNED messages from localhost; replies are only printed here");
    }

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
                                          @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                          HttpServletRequest request) {
        String raw = new String(body, StandardCharsets.UTF_8);
        if (properties.isDevMode()) {
            if (!isLoopback(request.getRemoteAddr())) {
                log.warn("WhatsApp dev-mode webhook rejected: not from localhost ({})", request.getRemoteAddr());
                return ResponseEntity.status(403).build();
            }
        } else if (!signatureVerifier.isValid(raw, signature)) {
            log.warn("WhatsApp webhook rejected: bad or missing signature");
            return ResponseEntity.status(403).build();
        }
        processor.process(raw);
        return ResponseEntity.ok("EVENT_RECEIVED");
    }

    static boolean isLoopback(String remoteAddr) {
        try {
            return remoteAddr != null && InetAddress.getByName(remoteAddr).isLoopbackAddress();
        } catch (Exception ex) {
            return false;
        }
    }
}
