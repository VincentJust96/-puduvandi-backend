package com.puduvandi.push.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Matches the shape of the browser's PushSubscription.toJSON() output exactly. */
@Schema(description = "A browser push subscription, as returned by PushManager.subscribe()")
public record SubscribeRequest(
    @NotBlank(message = "endpoint is required")
    String endpoint,

    @NotNull(message = "keys are required")
    @Valid
    Keys keys
) {
    public record Keys(
        @NotBlank(message = "p256dh key is required")
        String p256dh,

        @NotBlank(message = "auth key is required")
        String auth
    ) {}
}
