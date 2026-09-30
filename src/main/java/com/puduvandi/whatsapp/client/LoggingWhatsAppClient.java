package com.puduvandi.whatsapp.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Used while puduvandi.whatsapp.enabled=false (local/dev): logs instead of sending. */
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
        log.info("[whatsapp disabled] text to {}: {}", waId, body);
        return null;
    }

    @Override
    public boolean sendButtons(String waId, String body, List<Button> buttons) {
        log.info("[whatsapp disabled] buttons to {}: {} {}", waId, body, buttons);
        return false;
    }

    @Override
    public boolean sendList(String waId, String body, String buttonLabel, String sectionTitle, List<ListRow> rows) {
        log.info("[whatsapp disabled] list to {}: {} {}", waId, body, rows);
        return false;
    }
}
