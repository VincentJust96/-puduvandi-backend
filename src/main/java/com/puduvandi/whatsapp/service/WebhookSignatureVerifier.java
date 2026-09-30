package com.puduvandi.whatsapp.service;

import com.puduvandi.whatsapp.config.WhatsAppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Checks Meta's X-Hub-Signature-256 header ("sha256=" + HMAC-SHA256 of the raw
 * request body, keyed with the app secret). Fails closed: with no app secret
 * configured, nothing verifies.
 */
@Component
@RequiredArgsConstructor
public class WebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";

    private final WhatsAppProperties properties;

    public boolean isValid(String rawBody, String signatureHeader) {
        String secret = properties.getAppSecret();
        if (secret == null || secret.isBlank() || rawBody == null
                || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()).toLowerCase());
            return MessageDigest.isEqual(expected, actual); // constant-time
        } catch (Exception ex) {
            return false; // bad hex, etc.
        }
    }
}
