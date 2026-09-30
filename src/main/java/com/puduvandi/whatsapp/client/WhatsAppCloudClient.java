package com.puduvandi.whatsapp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.puduvandi.whatsapp.config.WhatsAppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to the WhatsApp Business Cloud API (POST /{phone-number-id}/messages).
 * Free-form messages only work inside the 24-hour window that opens when the
 * customer messages us — fine for the booking conversation; anything we start
 * ourselves later needs an approved template (not built yet).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "puduvandi.whatsapp.enabled", havingValue = "true")
public class WhatsAppCloudClient implements WhatsAppClient {

    private final RestClient restClient;
    private final String messagesPath;

    public WhatsAppCloudClient(WhatsAppProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getAccessToken())
                .build();
        this.messagesPath = "/" + props.getApiVersion() + "/" + props.getPhoneNumberId() + "/messages";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String sendText(String waId, String body) {
        Map<String, Object> payload = base(waId, "text");
        payload.put("text", Map.of("body", body, "preview_url", true));
        return post(payload);
    }

    @Override
    public boolean sendButtons(String waId, String body, List<Button> buttons) {
        Map<String, Object> interactive = new LinkedHashMap<>();
        interactive.put("type", "button");
        interactive.put("body", Map.of("text", body));
        interactive.put("action", Map.of("buttons", buttons.stream()
                .map(b -> Map.of("type", "reply", "reply", Map.of("id", b.id(), "title", clip(b.title(), 20))))
                .toList()));
        return post(interactive(waId, interactive)) != null;
    }

    @Override
    public boolean sendList(String waId, String body, String buttonLabel, String sectionTitle, List<ListRow> rows) {
        List<Map<String, String>> rowMaps = rows.stream()
                .map(r -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("id", r.id());
                    row.put("title", clip(r.title(), 24));
                    if (r.description() != null && !r.description().isBlank()) {
                        row.put("description", clip(r.description(), 72));
                    }
                    return row;
                })
                .toList();

        Map<String, Object> interactive = new LinkedHashMap<>();
        interactive.put("type", "list");
        interactive.put("body", Map.of("text", body));
        interactive.put("action", Map.of(
                "button", clip(buttonLabel, 20),
                "sections", List.of(Map.of("title", clip(sectionTitle, 24), "rows", rowMaps))));
        return post(interactive(waId, interactive)) != null;
    }

    // ===== Internal =====

    private Map<String, Object> interactive(String waId, Map<String, Object> interactive) {
        Map<String, Object> payload = base(waId, "interactive");
        payload.put("interactive", interactive);
        return payload;
    }

    private Map<String, Object> base(String waId, String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", waId);
        payload.put("type", type);
        return payload;
    }

    /** @return the wamid, or null if the API call failed (already logged) */
    private String post(Map<String, Object> payload) {
        try {
            JsonNode response = restClient.post()
                    .uri(messagesPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode id = response == null ? null : response.path("messages").path(0).path("id");
            return (id == null || id.isMissingNode()) ? "unknown" : id.asText();
        } catch (Exception ex) {
            log.error("WhatsApp send failed: to={}, error={}", payload.get("to"), ex.getMessage());
            return null;
        }
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
