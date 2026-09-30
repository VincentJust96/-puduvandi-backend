package com.puduvandi.whatsapp.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Used while puduvandi.whatsapp.enabled=false (local/dev): prints each outgoing message to
 * the console instead of sending it. Buttons and list rows show their reply id, which is
 * what a dev-mode test sends back as the "tap". Media "downloads" return a 1x1 placeholder
 * PNG, so a dev-mode test can send any media id as a licence photo.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "puduvandi.whatsapp.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingWhatsAppClient implements WhatsAppClient {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String sendText(String waId, String body) {
        print(waId, body, "");
        return null;
    }

    @Override
    public boolean sendButtons(String waId, String body, List<Button> buttons) {
        StringBuilder options = new StringBuilder();
        buttons.forEach(b -> options.append("\n  [").append(b.title()).append("]  reply id: ").append(b.id()));
        print(waId, body, options.toString());
        return false;
    }

    @Override
    public boolean sendList(String waId, String body, String buttonLabel, String sectionTitle, List<ListRow> rows) {
        StringBuilder options = new StringBuilder();
        rows.forEach(r -> {
            options.append("\n  • ").append(r.title());
            if (r.description() != null && !r.description().isBlank()) {
                options.append(" — ").append(r.description());
            }
            options.append("  reply id: ").append(r.id());
        });
        print(waId, body, options.toString());
        return false;
    }

    @Override
    public Optional<Media> downloadMedia(String mediaId, long maxBytes) {
        log.info("[whatsapp] media {} not downloaded (channel disabled) — using a placeholder image", mediaId);
        return Optional.of(new Media(PLACEHOLDER_PNG, "image/png"));
    }

    private static final byte[] PLACEHOLDER_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    private static void print(String waId, String body, String options) {
        log.info("[whatsapp → {}] (not sent — channel disabled)\n{}{}", waId, body, options);
    }
}
