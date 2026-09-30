package com.puduvandi.whatsapp.service;

import com.puduvandi.errorlog.service.ErrorLogService;
import com.puduvandi.whatsapp.client.WhatsAppClient;
import com.puduvandi.whatsapp.conversation.ProcessedWhatsAppMessage;
import com.puduvandi.whatsapp.conversation.ProcessedWhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs off the webhook thread so Meta gets its 200 straight away. Skips messages it has
 * already seen (Meta redelivers), and serializes messages from the same customer so a
 * double-tap can't race two handlers over one conversation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppInboundProcessor {

    private static final int LOCK_STRIPES = 64;

    private final WhatsAppWebhookParser parser;
    private final WhatsAppBotService botService;
    private final ProcessedWhatsAppMessageRepository processedRepository;
    private final WhatsAppClient client;
    private final ErrorLogService errorLogService;

    private final Object[] locks = createLocks();

    @Async
    public void process(String rawBody) {
        for (InboundMessage message : parser.parse(rawBody)) {
            if (!markProcessed(message.messageId())) {
                log.debug("Duplicate WhatsApp message ignored: {}", message.messageId());
                continue;
            }
            synchronized (locks[Math.floorMod(message.from().hashCode(), LOCK_STRIPES)]) {
                handleSafely(message);
            }
        }
    }

    private void handleSafely(InboundMessage message) {
        try {
            botService.handle(message);
        } catch (Exception ex) {
            log.error("WhatsApp message handling failed: messageId={}, error={}", message.messageId(), ex.getMessage(), ex);
            errorLogService.logServiceError(ex, "WhatsAppMessage", null, null);
            try {
                client.sendText(message.from(), "Sorry, something went wrong on our side 🙏 Please send HI to try again.");
            } catch (Exception ignored) {
                // nothing more we can do
            }
        }
    }

    /** @return false if this message id was already processed */
    private boolean markProcessed(String messageId) {
        try {
            processedRepository.saveAndFlush(new ProcessedWhatsAppMessage(messageId));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private static Object[] createLocks() {
        Object[] array = new Object[LOCK_STRIPES];
        for (int i = 0; i < array.length; i++) {
            array[i] = new Object();
        }
        return array;
    }
}
