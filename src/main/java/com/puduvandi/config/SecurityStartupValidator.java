package com.puduvandi.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fails fast (or at least screams loudly) if the app is about to run with the
 * known placeholder JWT secret that ships in application.yml for local dev,
 * or with a blank Razorpay/VAPID secret that silently no-ops payments/push.
 * <p>
 * - Any environment: logs an ERROR so it's impossible to miss in the logs.
 * - PUDUVANDI_ENV=production: throws to abort startup entirely — running
 *   production with a publicly-known signing secret would let anyone forge
 *   tokens, and running it with a blank Razorpay/VAPID secret means real
 *   payments/push are silently broken rather than failing loudly at boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityStartupValidator {

    /** The exact base64 placeholder committed in application.yml's default. */
    private static final String PLACEHOLDER_JWT_SECRET =
            "Y2hhbmdlLXRoaXMtc2VjcmV0LWluLXByb2R1Y3Rpb24tZW52aXJvbm1lbnQ=";

    private final JwtProperties jwtProperties;
    private final RazorpayConfig razorpayConfig;
    private final PushProperties pushProperties;

    /** Only warns (never blocks boot) — insurance-document-password encryption is a narrow,
     *  optional field, not core to auth/payments/push like the checks above. */
    @Value("${puduvandi.encryption.key:}")
    private String encryptionKey;

    @PostConstruct
    public void validate() {
        boolean production = "production".equals(System.getenv("PUDUVANDI_ENV"));

        if (PLACEHOLDER_JWT_SECRET.equals(jwtProperties.getSecret())) {
            log.error("SECURITY WARNING: JWT secret is still set to the default placeholder value. " +
                    "Set the JWT_SECRET environment variable to a strong, unique secret before going live.");
            failIfProduction(production,
                    "Refusing to start with the placeholder JWT secret while PUDUVANDI_ENV=production. " +
                    "Set the JWT_SECRET environment variable.");
        }

        if (isBlank(razorpayConfig.getKeySecret()) && !razorpayConfig.isMockEnabled()) {
            log.error("SECURITY WARNING: RAZORPAY_KEY_SECRET is blank while payment mock mode is disabled. " +
                    "Real Razorpay calls will fail authentication. Set the RAZORPAY_KEY_SECRET environment variable.");
            failIfProduction(production,
                    "Refusing to start with a blank RAZORPAY_KEY_SECRET while PUDUVANDI_ENV=production. " +
                    "Set the RAZORPAY_KEY_SECRET environment variable.");
        }

        if (isBlank(pushProperties.getVapidPrivateKey())) {
            log.error("SECURITY WARNING: VAPID_PRIVATE_KEY is blank. Web push sends will fail. " +
                    "Set the VAPID_PRIVATE_KEY environment variable.");
            failIfProduction(production,
                    "Refusing to start with a blank VAPID_PRIVATE_KEY while PUDUVANDI_ENV=production. " +
                    "Set the VAPID_PRIVATE_KEY environment variable.");
        }

        if (isBlank(encryptionKey)) {
            log.warn("ENCRYPTION_KEY is blank. Insurance-document-password encryption is disabled — " +
                    "reads of legacy/plaintext values still work, but new values cannot be saved. " +
                    "Set the ENCRYPTION_KEY environment variable (e.g. `openssl rand -base64 32`).");
        }
    }

    private void failIfProduction(boolean production, String message) {
        if (production) {
            throw new IllegalStateException(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
