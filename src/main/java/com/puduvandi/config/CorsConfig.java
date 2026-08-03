package com.puduvandi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Allows the React frontend to call this API.
 * Includes wildcard pattern for local network testing (phone over WiFi).
 * <p>
 * {@code https://*.vercel.app} is a shared public domain — anyone can deploy under it,
 * so combined with allowCredentials(true) it's only appropriate for the current
 * test/staging deployment (see project deployment notes), never production. Once a
 * real frontend domain exists, set CORS_EXTRA_ORIGINS to that exact origin (comma-separated
 * if more than one) instead of relying on this default.
 */
@Configuration
public class CorsConfig {

    @Value("${CORS_EXTRA_ORIGINS:https://*.vercel.app}")
    private String extraOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allows localhost (any port) AND any local network IP (192.168.x.x) on any port
        // This covers: web dev server, phone browser over WiFi, Capacitor app
        List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:*",
                "http://192.168.*.*:*",
                "capacitor://localhost",
                "http://localhost"
        ));
        if (extraOrigins != null && !extraOrigins.isBlank()) {
            Arrays.stream(extraOrigins.split(","))
                    .map(String::trim)
                    .filter(o -> !o.isEmpty())
                    .forEach(allowedOrigins::add);
        }
        configuration.setAllowedOriginPatterns(allowedOrigins);

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
