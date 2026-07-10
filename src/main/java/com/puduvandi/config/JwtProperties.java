package com.puduvandi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps JWT configuration from application.yml (puduvandi.jwt.*).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "puduvandi.jwt")
public class JwtProperties {

    /** Base64-encoded HMAC secret key */
    private String secret;

    /** Access token expiry in milliseconds (default: 15 min) */
    private long accessTokenExpiryMs;

    /** Refresh token expiry in milliseconds (default: 7 days) */
    private long refreshTokenExpiryMs;
}
