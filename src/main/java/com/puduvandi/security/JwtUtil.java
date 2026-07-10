package com.puduvandi.security;

import com.puduvandi.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * Utility for generating, parsing, and validating JWT tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    /**
     * Generates a signed JWT access token.
     *
     * @param userId    the user's database ID
     * @param phone     the user's phone number (subject)
     * @param role      the user's role (CUSTOMER/OWNER/ADMIN)
     * @return signed JWT string
     */
    public String generateAccessToken(Long userId, String phone, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiryMs());

        return Jwts.builder()
                .subject(phone)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts the phone number (subject) from a JWT token.
     */
    public String extractPhone(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the userId claim from a JWT token.
     */
    public Long extractUserId(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    /**
     * Extracts the role claim from a JWT token.
     */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Validates a JWT token's signature and expiry.
     *
     * @return true if valid, false otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token expired: {}", ex.getMessage());
        } catch (JwtException ex) {
            log.warn("JWT token invalid: {}", ex.getMessage());
        }
        return false;
    }

    // ===== Private Helpers =====

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
