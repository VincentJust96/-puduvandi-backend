package com.puduvandi.whatsapp.service;

/**
 * One customer message, flattened from Meta's nested webhook JSON.
 *
 * @param from      sender's WhatsApp id (digits, with country code)
 * @param messageId Meta's wamid — unique per message
 * @param type      text | interactive | image | location | ...
 * @param text      the typed text (type=text), else null
 * @param replyId   id of the tapped button/list row (type=interactive), else null
 */
public record InboundMessage(String from, String messageId, String type, String text, String replyId) {

    /** What the bot should react to: the tapped id if any, else the typed text; trimmed, never null. */
    public String input() {
        if (replyId != null && !replyId.isBlank()) {
            return replyId.trim();
        }
        return text == null ? "" : text.trim();
    }
}
