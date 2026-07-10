package com.puduvandi.config;

import com.puduvandi.security.PuduvandiUserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Enables JPA Auditing for created_by / updated_by fields in BaseEntity.
 * Automatically reads the phone number of the currently authenticated user.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    /**
     * Provides the current user's phone as the auditor.
     * Returns "system" for unauthenticated operations (e.g. registration).
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated()) {
                return Optional.of("system");
            }

            Object principal = auth.getPrincipal();
            if (principal instanceof PuduvandiUserPrincipal userPrincipal) {
                return Optional.of(userPrincipal.getPhone());
            }

            return Optional.of("system");
        };
    }
}
