package com.puduvandi.realtime;

import com.puduvandi.security.PuduvandiUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Realtime", description = "Server-Sent Events stream for live UI updates")
@SecurityRequirement(name = "bearerAuth")
public class RealtimeController {

    /** 30 minutes — long enough to cover a browsing session; the frontend reconnects on drop. */
    private static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;

    private final SseConnectionRegistry registry;

    @GetMapping(value = "/stream", produces = "text/event-stream")
    @Operation(summary = "Open a live SSE stream of realtime events for the authenticated user")
    public SseEmitter stream(@AuthenticationPrincipal PuduvandiUserPrincipal principal) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        registry.register(principal.getUserId(), principal.getRole(), emitter);

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception ex) {
            log.debug("Failed to send initial SSE ping for userId={}: {}", principal.getUserId(), ex.getMessage());
        }

        return emitter;
    }
}
