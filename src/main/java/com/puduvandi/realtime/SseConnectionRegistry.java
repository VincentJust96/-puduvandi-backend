package com.puduvandi.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory registry of open SSE connections, keyed by userId. Fine for a
 * single-node deployment (same caveat as RateLimitFilter's in-memory buckets) —
 * would need a shared pub/sub (Redis, etc.) behind a load balancer with more
 * than one app instance.
 */
@Slf4j
@Component
public class SseConnectionRegistry {

    private record Registration(SseEmitter emitter, String role) {}

    private final Map<Long, CopyOnWriteArrayList<Registration>> byUser = new ConcurrentHashMap<>();

    public void register(Long userId, String role, SseEmitter emitter) {
        CopyOnWriteArrayList<Registration> regs = byUser.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        regs.add(new Registration(emitter, role));
        log.info("SSE registered: userId={}, role={}, connectionsForUser={}", userId, role, regs.size());

        emitter.onCompletion(() -> {
            log.info("SSE onCompletion: userId={}", userId);
            deregister(userId, emitter);
        });
        emitter.onTimeout(() -> {
            log.info("SSE onTimeout: userId={}", userId);
            deregister(userId, emitter);
        });
        emitter.onError(ex -> {
            log.info("SSE onError: userId={}, error={}", userId, ex.getMessage());
            deregister(userId, emitter);
        });
    }

    private void deregister(Long userId, SseEmitter emitter) {
        byUser.computeIfPresent(userId, (id, regs) -> {
            regs.removeIf(r -> r.emitter() == emitter);
            return regs.isEmpty() ? null : regs;
        });
    }

    public void sendToUser(Long userId, RealtimeEvent event) {
        List<Registration> regs = byUser.get(userId);
        log.info("SSE sendToUser: userId={}, activeConnections={}", userId, regs == null ? 0 : regs.size());
        if (regs == null || regs.isEmpty()) {
            return;
        }
        for (Registration reg : List.copyOf(regs)) {
            send(userId, reg, event);
        }
    }

    public void sendToUsers(Collection<Long> userIds, RealtimeEvent event) {
        for (Long userId : userIds) {
            sendToUser(userId, event);
        }
    }

    public void sendToRole(String role, RealtimeEvent event) {
        byUser.forEach((userId, regs) -> {
            for (Registration reg : List.copyOf(regs)) {
                if (role.equals(reg.role())) {
                    send(userId, reg, event);
                }
            }
        });
    }

    /** Comment-only ping to every open connection — detects and prunes dead ones. */
    public void pingAll() {
        byUser.forEach((userId, regs) -> {
            for (Registration reg : List.copyOf(regs)) {
                try {
                    reg.emitter().send(SseEmitter.event().comment("ping"));
                } catch (Exception ex) {
                    deregister(userId, reg.emitter());
                }
            }
        });
    }

    private void send(Long userId, Registration reg, RealtimeEvent event) {
        try {
            reg.emitter().send(SseEmitter.event()
                    .name(event.type().name().toLowerCase())
                    .data(event, MediaType.APPLICATION_JSON));
            log.info("SSE sent: userId={}, type={}", userId, event.type());
        } catch (IOException | IllegalStateException ex) {
            log.warn("SSE send failed for userId={}, deregistering: {}", userId, ex.getMessage());
            deregister(userId, reg.emitter());
        }
    }
}
