package com.puduvandi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps Razorpay configuration from application.yml (puduvandi.razorpay.*).
 * No SDK client is built here — PaymentService constructs a RazorpayClient
 * lazily, only when it actually needs to call the API, so a blank key
 * pair never blocks startup while mock-enabled is true.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "puduvandi.razorpay")
public class RazorpayConfig {

    private String keyId;
    private String keySecret;
    private boolean mockEnabled = true;
    private int paymentExpiryMinutes = 15;
}
