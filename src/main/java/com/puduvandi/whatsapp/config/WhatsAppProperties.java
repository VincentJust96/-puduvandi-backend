package com.puduvandi.whatsapp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps puduvandi.whatsapp.* from application.yml — the WhatsApp Business Cloud API
 * credentials and bot settings. Secrets belong only in the deployment environment.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "puduvandi.whatsapp")
public class WhatsAppProperties {

    /** false = bot endpoints are off and outbound messages are only logged. */
    private boolean enabled = false;

    /**
     * Local testing without Meta: the webhook stays on but accepts unsigned requests from
     * localhost only, and every reply is printed to the console instead of sent.
     * Must never be switched on together with {@link #enabled}.
     */
    private boolean devMode = false;

    /** Meta "Phone number ID" of the Puduvandi WhatsApp Business number. */
    private String phoneNumberId;

    /** Permanent system-user access token. */
    private String accessToken;

    /** Meta app secret — used to verify the X-Hub-Signature-256 header on every webhook POST. */
    private String appSecret;

    /** Arbitrary string you also type into Meta's webhook setup screen (GET handshake). */
    private String verifyToken;

    private String apiVersion = "v21.0";
    private String baseUrl = "https://graph.facebook.com";

    /** Base URL of the no-login payment page (PaymentLinkController); a signed token is appended. */
    private String paymentPageUrl = "http://localhost:8080/api/v1/pay";

    /** Public website, mentioned when the customer must finish something outside WhatsApp. */
    private String websiteUrl = "https://puduvandi.com";

    /** A conversation idle for longer than this restarts from the greeting. */
    private int sessionTtlMinutes = 30;
}
