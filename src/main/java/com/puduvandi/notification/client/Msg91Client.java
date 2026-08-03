package com.puduvandi.notification.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puduvandi.config.Msg91Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for MSG91's transactional SMS API.
 * <p>
 * The request body below follows MSG91's documented v5 "SMS" endpoint shape
 * (sender/route/country/DLT_TE_ID + a "sms" array of {message, to}), which
 * takes the final message text directly rather than per-variable template
 * substitution — a better fit for this app's fully-composed message strings
 * than the variable-based Flow API. Confirm this against the sample code
 * MSG91's dashboard generates for your specific approved template
 * (Settings -> API -> your template -> Sample Code) before relying on it in
 * production — field names for DLT-compliant sends are template/account
 * specific and MSG91's dashboard is the source of truth, not this comment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Msg91Client {

    private final Msg91Properties msg91Properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Msg91SendResult send(String toPhone, String message, String dltTemplateId) {
        if (!msg91Properties.isConfigured()) {
            return Msg91SendResult.failure("MSG91 authkey not configured");
        }
        if (dltTemplateId == null || dltTemplateId.isBlank()) {
            return Msg91SendResult.failure("No MSG91 DLT template ID configured for this message type");
        }

        try {
            Map<String, Object> smsEntry = new LinkedHashMap<>();
            smsEntry.put("message", message);
            smsEntry.put("to", List.of(toPhone.replace("+", "")));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sender", msg91Properties.getSenderId());
            body.put("route", msg91Properties.getRoute());
            body.put("country", msg91Properties.getCountry());
            body.put("DLT_TE_ID", dltTemplateId);
            body.put("sms", List.of(smsEntry));

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(msg91Properties.getApiUrl()))
                    .header("authkey", msg91Properties.getAuthkey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("MSG91 SMS sent: phone={}, status={}", toPhone, response.statusCode());
                return Msg91SendResult.ok();
            }

            log.warn("MSG91 SMS failed: phone={}, status={}, body={}", toPhone, response.statusCode(), response.body());
            return Msg91SendResult.failure("MSG91 responded " + response.statusCode() + ": " + response.body());
        } catch (Exception ex) {
            log.error("MSG91 SMS request failed: phone={}", toPhone, ex);
            return Msg91SendResult.failure(ex.getMessage());
        }
    }
}
