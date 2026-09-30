package com.puduvandi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps Web Push (VAPID) configuration from application.yml (puduvandi.push.*).
 * The dev-default keypair below is a real, generated VAPID keypair — fine for
 * local/dev push testing, same convention as JWT_SECRET's dev default;
 * override via env vars for staging/prod.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "puduvandi.push")
public class PushProperties {

    private String vapidPublicKey;
    private String vapidPrivateKey;
    private String subject;
}
