package com.puduvandi.whatsapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls customer messages out of Meta's webhook body
 * ({@code entry[].changes[].value.messages[]}). Delivery/read status updates
 * carry no {@code messages} array and are ignored.
 */
@Component
public class WhatsAppWebhookParser {

    private final ObjectMapper objectMapper;

    public WhatsAppWebhookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<InboundMessage> parse(String rawBody) {
        List<InboundMessage> result = new ArrayList<>();
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            return result;
        }
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode message : change.path("value").path("messages")) {
                    String from = message.path("from").asText(null);
                    String id = message.path("id").asText(null);
                    if (from == null || id == null) {
                        continue;
                    }
                    String type = message.path("type").asText("unknown");
                    String text = message.path("text").path("body").asText(null);

                    JsonNode interactive = message.path("interactive");
                    String replyId = null;
                    if (!interactive.isMissingNode()) {
                        replyId = firstNonBlank(
                                interactive.path("button_reply").path("id").asText(null),
                                interactive.path("list_reply").path("id").asText(null));
                    }
                    result.add(new InboundMessage(from, id, type, text, replyId));
                }
            }
        }
        return result;
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
