package com.puduvandi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps OTP configuration from application.yml (puduvandi.otp.*).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "puduvandi.otp")
public class OtpProperties {

    /** OTP expiry duration in minutes */
    private int expiryMinutes;

    /** Number of digits in the OTP */
    private int length;

    /** If true, always return mockOtp instead of real SMS (for development) */
    private boolean mockEnabled;

    /** The OTP value used when mockEnabled=true */
    private String mockOtp;
}
