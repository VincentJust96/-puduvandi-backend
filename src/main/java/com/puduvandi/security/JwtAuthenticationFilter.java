package com.puduvandi.security;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.common.enums.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Intercepts every HTTP request, extracts the JWT from Authorization header,
 * validates it, and sets the authentication in the SecurityContext.
 * <p>
 * Flow: Request → Extract Token → Validate → Set Authentication → Continue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtUtil.isTokenValid(token)) {
            setAuthenticationToContext(token);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Reads the Bearer token from Authorization header.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Builds a Spring Security authentication object from JWT claims
     * and places it in the SecurityContext.
     */
    private void setAuthenticationToContext(String token) {
        try {
            String phone  = jwtUtil.extractPhone(token);
            Long userId   = jwtUtil.extractUserId(token);
            String role   = jwtUtil.extractRole(token);

            // Reject tokens belonging to suspended or deleted users
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty() || userOpt.get().isDeleted()
                    || userOpt.get().getStatus() == UserStatus.SUSPENDED) {
                log.debug("Rejecting token for suspended/deleted userId={}", userId);
                return;
            }

            // "NEW_USER" is a placeholder role for users who have not yet selected CUSTOMER/OWNER.
            // It grants no resource access but allows calling /auth/set-role (which is permitAll).
            String authority = (role != null) ? role : "NEW_USER";
            var authentication = new UsernamePasswordAuthenticationToken(
                    new PuduvandiUserPrincipal(userId, phone, role),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + authority))
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated user: phone={}, role={}", phone, role);

        } catch (Exception ex) {
            log.warn("Failed to set authentication from token: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
    }
}
