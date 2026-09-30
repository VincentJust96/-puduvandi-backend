package com.puduvandi.realtime.task;

import com.puduvandi.realtime.SseConnectionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps SSE connections alive through proxies that close idle streams, and
 * prunes dead ones (client closed tab, network drop) that never fired an
 * onError/onCompletion callback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseHeartbeatTask {

    private final SseConnectionRegistry registry;

    @Scheduled(fixedDelay = 25000)
    public void ping() {
        try {
            registry.pingAll();
        } catch (Exception ex) {
            log.error("SSE heartbeat sweep failed", ex);
        }
    }
}
