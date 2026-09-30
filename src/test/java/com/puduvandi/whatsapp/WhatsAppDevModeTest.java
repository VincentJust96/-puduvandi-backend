package com.puduvandi.whatsapp;

import com.puduvandi.whatsapp.config.WhatsAppProperties;
import com.puduvandi.whatsapp.controller.WhatsAppWebhookController;
import com.puduvandi.whatsapp.service.WebhookSignatureVerifier;
import com.puduvandi.whatsapp.service.WhatsAppInboundProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("WhatsApp webhook dev mode")
class WhatsAppDevModeTest {

    private static final byte[] BODY = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);

    private WhatsAppProperties props;
    private WhatsAppInboundProcessor processor;
    private WhatsAppWebhookController controller;

    @BeforeEach
    void setUp() {
        props = new WhatsAppProperties();
        props.setAppSecret("app-secret");
        processor = mock(WhatsAppInboundProcessor.class);
        controller = new WhatsAppWebhookController(props, new WebhookSignatureVerifier(props), processor);
    }

    private static MockHttpServletRequest from(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    @DisplayName("dev mode accepts an unsigned message from localhost (IPv4 and IPv6)")
    void devModeLocalhost() {
        props.setDevMode(true);

        assertThat(controller.receive(BODY, null, from("127.0.0.1")).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.receive(BODY, null, from("0:0:0:0:0:0:0:1")).getStatusCode().value()).isEqualTo(200);
        verify(processor, times(2)).process(anyString());
    }

    @Test
    @DisplayName("dev mode rejects anything not from localhost, even with a forged forwarded header")
    void devModeRemote() {
        props.setDevMode(true);
        MockHttpServletRequest request = from("203.0.113.9");
        request.addHeader("X-Forwarded-For", "127.0.0.1");

        assertThat(controller.receive(BODY, null, request).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.receive(BODY, null, from("10.0.0.5")).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("outside dev mode an unsigned message is rejected even from localhost")
    void normalModeNeedsSignature() {
        assertThat(controller.receive(BODY, null, from("127.0.0.1")).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("refuses to start with dev mode and the real channel both on")
    void devModeAndEnabledConflict() {
        props.setDevMode(true);
        props.setEnabled(true);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(controller, "checkMode"))
                .isInstanceOf(IllegalStateException.class);
    }
}
