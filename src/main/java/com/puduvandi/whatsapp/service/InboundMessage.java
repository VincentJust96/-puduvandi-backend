package com.puduvandi.whatsapp.service;

/**
 * One customer message, flattened from Meta's nested webhook JSON.
 *
 * @param from          sender's WhatsApp id (digits, with country code)
 * @param messageId     Meta's wamid — unique per message
 * @param type          text | interactive | image | document | location | ...
 * @param text          the typed text (type=text), else null
 * @param replyId       id of the tapped button/list row (type=interactive), else null
 * @param mediaId       Meta media id of a photo/file (type=image|document), else null
 * @param mediaMimeType its MIME type as Meta reports it, else null
 */
public record InboundMessage(String from, String messageId, String type, String text, String replyId,
                             String mediaId, String mediaMimeType) {

    public InboundMessage(String from, String messageId, String type, String text, String replyId) {
        this(from, messageId, type, text, replyId, null, null);
    }

    /** What the bot should react to: the tapped id if any, else the typed text; trimmed, never null. */
    public String input() {
        if (replyId != null && !replyId.isBlank()) {
            return replyId.trim();
        }
        return text == null ? "" : text.trim();
    }

    public boolean hasMedia() {
        return mediaId != null && !mediaId.isBlank();
    }
}
