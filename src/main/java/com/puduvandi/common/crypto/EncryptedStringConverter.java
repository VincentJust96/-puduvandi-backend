package com.puduvandi.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES/GCM string encryption at rest for narrow, sensitive-but-rarely-touched
 * columns (currently just Bike.insuranceDocumentPassword). Spring-managed
 * (Boot auto-configures Hibernate's bean container so a @Component converter
 * gets @Value injection instead of a bare no-arg instantiation).
 * <p>
 * Ciphertext is prefixed "v1:" so legacy plaintext values (existing seed data,
 * written before this converter existed) are recognized and passed through
 * unchanged on read rather than misinterpreted as ciphertext — see
 * EncryptedInsuranceDocumentPasswordMigrator for the one-time upgrade pass.
 * <p>
 * Read-path failures (missing/wrong key, corrupted ciphertext) are logged and
 * return null rather than throwing: this field is on the main Bike entity, so
 * an unconditional throw here would break every single bike read (listing,
 * booking, dashboards), not just the narrow admin-only path that actually uses
 * this value.
 * <p>
 * Write-path is the same story, for a subtler reason: JPA/Hibernate calls
 * convertToDatabaseColumn() whenever a row's UPDATE statement includes this
 * column — which happens on ANY flush of the entity (e.g. Bike.totalTrips++
 * on booking creation), not only when insuranceDocumentPassword itself was
 * just set. A legacy plaintext value being re-persisted unchanged looks
 * identical to a brand-new plaintext value needing encryption (neither has
 * the "v1:" prefix), so this converter cannot tell them apart. Throwing here
 * when the key is unconfigured was found live to break booking creation
 * entirely for any bike with a legacy password value already in that column —
 * far outside the narrow admin-only feature this converter is meant to guard.
 * So: log loudly and store the value unencrypted rather than throw. This is
 * best-effort encryption, not a hard guarantee — once ENCRYPTION_KEY is set
 * in every environment that touches this column, writes succeed and get
 * properly encrypted; until then we degrade to "stored as given" instead of
 * taking down unrelated business operations.
 */
@Slf4j
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String CIPHERTEXT_PREFIX = "v1:";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Base64-encoded 32-byte AES-256 key. Blank by default — no committed real
     *  default, same rule as razorpay.key-secret/vapid-private-key. */
    @Value("${puduvandi.encryption.key:}")
    private String base64Key;

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (plaintext.startsWith(CIPHERTEXT_PREFIX)) {
            return plaintext; // already encrypted — re-saving an already-loaded entity
        }
        if (!isKeyConfigured()) {
            log.error("ENCRYPTION_KEY is not configured — storing insurance-document-password " +
                    "value unencrypted. Set ENCRYPTION_KEY before going live.");
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return CIPHERTEXT_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            log.error("Failed to encrypt insurance-document-password value — storing unencrypted", ex);
            return plaintext;
        }
    }

    @Override
    public String convertToEntityAttribute(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        if (!storedValue.startsWith(CIPHERTEXT_PREFIX)) {
            return storedValue; // legacy plaintext, written before this converter existed
        }
        try {
            byte[] combined = Base64.getDecoder().decode(storedValue.substring(CIPHERTEXT_PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("Failed to decrypt stored value (missing/rotated ENCRYPTION_KEY or corrupted data): {}",
                    ex.getMessage());
            return null;
        }
    }

    boolean isKeyConfigured() {
        return base64Key != null && !base64Key.isBlank();
    }

    private SecretKeySpec resolveKey() {
        if (!isKeyConfigured()) {
            throw new IllegalStateException("ENCRYPTION_KEY is not configured");
        }
        return new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
    }
}
