package com.puduvandi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Allows the React frontend to call this API.
 * Includes wildcard pattern for local network testing (phone over WiFi).
 * In production, restrict allowedOriginPatterns to your real frontend domain.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allows localhost (any port) AND any local network IP (192.168.x.x) on any port
        // This covers: web dev server, phone browser over WiFi, Capacitor app
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://192.168.*.*:*",
                "capacitor://localhost",
                "http://localhost",
                "https://*.vercel.app"
        ));

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
