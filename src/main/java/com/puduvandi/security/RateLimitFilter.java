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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
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

    /**
     * Comma-separated IPs/CIDRs of reverse proxies actually in front of this app
     * (see application.yml). Empty by default — X-Forwarded-For is never trusted
     * unless the direct connection is from one of these.
     */
    @Value("${puduvandi.security.trusted-proxies:}")
    private String trustedProxiesRaw;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && SEND_OTP_PATH.equals(path)) {
            applyBodyPhoneLimit(request, response, filterChain, "send-otp", 5, Duration.ofMinutes(15), 30);
            return;
        }

        if ("POST".equals(method) && VERIFY_OTP_PATH.equals(path)) {
            applyBodyPhoneLimit(request, response, filterChain, "verify-otp", 10, Duration.ofMinutes(15), 60);
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
                                      int capacity, Duration window, int ipCapacity) throws IOException, ServletException {

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);

        // Aggregate per-IP cap, checked in ADDITION to the per-phone bucket below — without
        // this, an attacker rotating through many phone numbers gets a fresh 5/10-request
        // bucket per number with no ceiling on the total from one source, defeating the
        // per-phone limit's purpose (SMS-cost abuse / phone enumeration).
        String ipKey = bucketPrefix + ":ip:" + clientIp(request);
        if (!tryConsume(ipKey, ipCapacity, window)) {
            rejectWithTooManyRequests(response);
            return;
        }

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
        String remoteAddr = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        // X-Forwarded-For is fully attacker-controlled unless it arrives via a proxy we
        // actually trust — otherwise any caller can set it to a fresh IP on every request
        // to bypass the per-IP rate-limit cap entirely. Only honor it when the direct
        // connection is from a configured trusted proxy.
        if (forwarded != null && !forwarded.isBlank() && isTrustedProxy(remoteAddr)) {
            return forwarded.split(",")[0].trim();
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (trustedProxiesRaw == null || trustedProxiesRaw.isBlank()) {
            return false;
        }
        List<String> entries = Arrays.stream(trustedProxiesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        for (String entry : entries) {
            if (entry.contains("/")) {
                if (isInCidrRange(remoteAddr, entry)) {
                    return true;
                }
            } else if (entry.equals(remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInCidrRange(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/", 2);
            InetAddress targetAddr = InetAddress.getByName(ip);
            InetAddress rangeAddr = InetAddress.getByName(parts[0]);
            byte[] targetBytes = targetAddr.getAddress();
            byte[] rangeBytes = rangeAddr.getAddress();
            if (targetBytes.length != rangeBytes.length) {
                return false;
            }
            int prefixLength = Integer.parseInt(parts[1]);
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (targetBytes[i] != rangeBytes[i]) {
                    return false;
                }
            }
            if (remainingBits > 0) {
                int mask = 0xFF << (8 - remainingBits);
                if ((targetBytes[fullBytes] & mask) != (rangeBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException | NumberFormatException | ArrayIndexOutOfBoundsException ex) {
            log.warn("Invalid trusted-proxy CIDR entry '{}': {}", cidr, ex.getMessage());
            return false;
        }
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
