package com.puduvandi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps MSG91 configuration from application.yml (puduvandi.msg91.*).
 * Blank authkey means MSG91 isn't set up yet (no DLT approval), so
 * NotificationService falls back to its existing no-provider-configured stub —
 * same convention Razorpay/Push use for "not configured yet" defaults.
 * <p>
 * The per-purpose template IDs are MSG91 DLT Flow/Template IDs — they only
 * exist once the corresponding message content is DLT-approved (see
 * automation/n8n/MSG91_SETUP.md). apiUrl defaults to MSG91's v5 SMS endpoint;
 * confirm the exact request shape against MSG91's dashboard sample code for
 * your approved template before relying on it — the JSON body MSG91 expects
 * is account/template-specific.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "puduvandi.msg91")
public class Msg91Properties {

    private String authkey;
    private String senderId;
    private String route = "4";     // 4 = transactional route
    private String country = "91";
    private String apiUrl = "https://control.msg91.com/api/v5/sms";

    private String bookingConfirmationTemplateId;
    private String pickupReminderTemplateId;
    private String rideCompletionTemplateId;
    private String otpTemplateId;

    public boolean isConfigured() {
        return authkey != null && !authkey.isBlank();
    }
}
