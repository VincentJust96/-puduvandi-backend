package com.puduvandi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps security-deposit configuration from application.yml (puduvandi.deposit.*).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "puduvandi.deposit")
public class DepositProperties {

    /** Hours after a booking completes before its unclaimed deposit auto-refunds. */
    private int autoReleaseHours = 48;
}
