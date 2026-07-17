package com.puduvandi.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fails fast (or at least screams loudly) if the app is about to run with the
 * known placeholder JWT secret that ships in application.yml for local dev.
 * <p>
 * - Any environment: logs an ERROR so it's impossible to miss in the logs.
 * - PUDUVANDI_ENV=production: throws to abort startup entirely — running
 *   production with a publicly-known signing secret would let anyone forge tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityStartupValidator {

    /** The exact base64 placeholder committed in application.yml's default. */
    private static final String PLACEHOLDER_JWT_SECRET =
            "Y2hhbmdlLXRoaXMtc2VjcmV0LWluLXByb2R1Y3Rpb24tZW52aXJvbm1lbnQ=";

    private final JwtProperties jwtProperties;

    @PostConstruct
    public void validate() {
        if (PLACEHOLDER_JWT_SECRET.equals(jwtProperties.getSecret())) {
            log.error("SECURITY WARNING: JWT secret is still set to the default placeholder value. " +
                    "Set the JWT_SECRET environment variable to a strong, unique secret before going live.");

            String env = System.getenv("PUDUVANDI_ENV");
            if ("production".equals(env)) {
                throw new IllegalStateException(
                        "Refusing to start with the placeholder JWT secret while PUDUVANDI_ENV=production. " +
                        "Set the JWT_SECRET environment variable.");
            }
        }
    }
}
