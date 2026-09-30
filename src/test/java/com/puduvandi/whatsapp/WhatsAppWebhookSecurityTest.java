package com.puduvandi.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puduvandi.whatsapp.config.WhatsAppProperties;
import com.puduvandi.whatsapp.service.InboundMessage;
import com.puduvandi.whatsapp.service.WebhookSignatureVerifier;
import com.puduvandi.whatsapp.service.WhatsAppWebhookParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WhatsApp webhook signature + parsing")
class WhatsAppWebhookSecurityTest {

    private static final String SECRET = "app-secret";
    private WhatsAppProperties props;
    private WebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        props = new WhatsAppProperties();
        props.setAppSecret(SECRET);
        verifier = new WebhookSignatureVerifier(props);
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("accepts a correctly signed body")
    void validSignature() throws Exception {
        String body = "{\"entry\":[]}";
        assertThat(verifier.isValid(body, sign(body, SECRET))).isTrue();
    }

    @Test
    @DisplayName("rejects wrong secret, tampered body, missing/garbled header")
    void invalidSignatures() throws Exception {
        String body = "{\"entry\":[]}";
        assertThat(verifier.isValid(body, sign(body, "other-secret"))).isFalse();
        assertThat(verifier.isValid(body + " ", sign(body, SECRET))).isFalse();
        assertThat(verifier.isValid(body, null)).isFalse();
        assertThat(verifier.isValid(body, "sha256=zzzz")).isFalse();
        assertThat(verifier.isValid(body, "md5=abc")).isFalse();
    }

    @Test
    @DisplayName("fails closed when no app secret is configured")
    void noSecretConfigured() throws Exception {
        String body = "{}";
        String header = sign(body, SECRET); // a signature that WOULD be valid if a secret were set
        props.setAppSecret("");
        assertThat(verifier.isValid(body, header)).isFalse();
    }

    @Test
    @DisplayName("parses text and interactive replies, ignores status updates")
    void parsesMessages() {
        String body = """
            {"entry":[{"changes":[{"value":{"messages":[
              {"from":"919876543210","id":"wamid.1","type":"text","text":{"body":"Hi"}},
              {"from":"919876543210","id":"wamid.2","type":"interactive",
               "interactive":{"type":"list_reply","list_reply":{"id":"area:Pondy","title":"Pondy"}}},
              {"from":"919876543210","id":"wamid.3","type":"interactive",
               "interactive":{"type":"button_reply","button_reply":{"id":"confirm:yes","title":"Book now"}}}
            ]}},{"value":{"statuses":[{"id":"wamid.9","status":"read"}]}}]}]}
            """;
        List<InboundMessage> messages = new WhatsAppWebhookParser(new ObjectMapper()).parse(body);

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).input()).isEqualTo("Hi");
        assertThat(messages.get(1).input()).isEqualTo("area:Pondy");
        assertThat(messages.get(2).input()).isEqualTo("confirm:yes");
    }

    @Test
    @DisplayName("garbage JSON yields no messages instead of throwing")
    void garbage() {
        assertThat(new WhatsAppWebhookParser(new ObjectMapper()).parse("not json")).isEmpty();
    }
}
