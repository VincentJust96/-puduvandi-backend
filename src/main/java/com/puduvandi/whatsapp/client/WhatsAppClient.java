package com.puduvandi.whatsapp.client;

import java.util.List;

/**
 * Outbound side of the WhatsApp channel. Implementations never throw for a
 * delivery problem the caller can't fix — they return false and log instead.
 */
public interface WhatsAppClient {

    record Button(String id, String title) {}

    record ListRow(String id, String title, String description) {}

    /** false when the channel is switched off (messages are only logged). */
    boolean isEnabled();

    /** @return the Meta message id (wamid), or null when nothing was sent */
    String sendText(String waId, String body);

    /** Up to 3 quick-reply buttons (title max 20 chars). */
    boolean sendButtons(String waId, String body, List<Button> buttons);

    /** A tap-to-open list, up to 10 rows (title max 24 chars, description max 72). */
    boolean sendList(String waId, String body, String buttonLabel, String sectionTitle, List<ListRow> rows);
}
