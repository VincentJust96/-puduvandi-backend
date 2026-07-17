package com.puduvandi.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple in-memory rate limiter (bucket4j token buckets keyed by phone number
 * or, failing that, client IP) for the few endpoints most exposed to abuse:
 * OTP send/verify and handover-OTP generation.
 * <p>
 * Note: in-memory buckets are per-instance. Fine for a single-node deployment;
 * would need a shared store (Redis, etc.) behind a load balancer.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String SEND_OTP_PATH = "/api/v1/auth/send-otp";
    private static final String VERIFY_OTP_PATH = "/api/v1/auth/verify-otp";

    private static final Pattern HANDOVER_GENERATE_PATTERN =
            Pattern.compile("^/api/v1/bookings/([^/]+)/handover/([^/]+)/generate$");

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && SEND_OTP_PATH.equals(path)) {
            applyBodyPhoneLimit(request, response, filterChain, "send-otp", 5, Duration.ofMinutes(15));
            return;
        }

        if ("POST".equals(method) && VERIFY_OTP_PATH.equals(path)) {
            applyBodyPhoneLimit(request, response, filterChain, "verify-otp", 10, Duration.ofMinutes(15));
            return;
        }

        if ("POST".equals(method)) {
            Matcher handoverMatcher = HANDOVER_GENERATE_PATTERN.matcher(path);
            if (handoverMatcher.matches()) {
                String bookingId = handoverMatcher.group(1);
                String purpose = handoverMatcher.group(2).toUpperCase();
                String key = "handover-generate:" + bookingId + ":" + purpose;
                if (!tryConsume(key, 5, Duration.ofMinutes(10))) {
                    rejectWithTooManyRequests(response);
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void applyBodyPhoneLimit(HttpServletRequest request, HttpServletResponse response,
                                      FilterChain filterChain, String bucketPrefix,
                                      int capacity, Duration window) throws IOException, ServletException {

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
        String key = bucketPrefix + ":" + resolveRateLimitKey(wrapped, request);

        if (!tryConsume(key, capacity, window)) {
            rejectWithTooManyRequests(response);
            return;
        }
        filterChain.doFilter(wrapped, response);
    }

    private String resolveRateLimitKey(CachedBodyHttpServletRequest wrapped, HttpServletRequest original) {
        try {
            String body = wrapped.getCachedBodyAsString();
            if (body != null && !body.isBlank()) {
                JsonNode node = objectMapper.readTree(body);
                JsonNode phoneNode = node.get("phoneNumber");
                if (phoneNode != null && !phoneNode.isNull() && !phoneNode.asText().isBlank()) {
                    return "phone:" + phoneNode.asText();
                }
            }
        } catch (Exception ex) {
            log.debug("Could not parse phoneNumber from request body for rate limiting; falling back to IP: {}",
                    ex.getMessage());
        }
        return "ip:" + clientIp(original);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean tryConsume(String key, int capacity, Duration window) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(capacity, window));
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(int capacity, Duration window) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, window));
        return Bucket.builder().addLimit(limit).build();
    }

    private void rejectWithTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Too many requests. Please try again later.\",\"errors\":null}");
    }
}
