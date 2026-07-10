package com.puduvandi.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps Twilio configuration from application.yml (puduvandi.twilio.*)
 * and initializes the Twilio SDK once credentials are available.
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "puduvandi.twilio")
public class TwilioConfig {

    /** Twilio Account SID */
    private String accountSid;

    /** Twilio Auth Token */
    private String authToken;

    /** SMS-capable Twilio number, e.g. +1234567890 */
    private String phoneNumber;

    /** WhatsApp-enabled Twilio number, e.g. whatsapp:+1234567890 */
    private String whatsappNumber;

    @PostConstruct
    public void init() {
        if (isBlank(accountSid) || isBlank(authToken)) {
            log.warn("Twilio credentials are not configured — SMS/WhatsApp notifications will fail until " +
                    "TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN are set.");
            return;
        }
        Twilio.init(accountSid, authToken);
        log.info("Twilio SDK initialized");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank() || value.startsWith("YOUR_");
    }
}
