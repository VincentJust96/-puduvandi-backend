package com.puduvandi.common.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time, idempotent upgrade pass: bikes.insurance_document_password was
 * stored in plaintext before EncryptedStringConverter existed. On boot, finds
 * any row whose stored value doesn't start with the "v1:" ciphertext marker
 * and rewrites it as ciphertext via raw JDBC (deliberately bypassing the JPA
 * entity/repository — Hibernate's dirty-checking compares the Java-level
 * decrypted String, which would be unchanged after a plain fetch+save, so no
 * UPDATE would ever fire that way). Only ever touches seed/demo data at this
 * project's current stage — there is no real production data yet.
 * <p>
 * Skips entirely if ENCRYPTION_KEY isn't configured: encrypting without a
 * stable, reusable key would just lock the data behind a key that won't
 * persist across restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncryptedInsuranceDocumentPasswordMigrator {

    private final JdbcTemplate jdbcTemplate;
    private final EncryptedStringConverter encryptedStringConverter;

    @EventListener(ApplicationReadyEvent.class)
    public void upgradeLegacyPlaintextRows() {
        if (!encryptedStringConverter.isKeyConfigured()) {
            log.info("ENCRYPTION_KEY not configured — skipping insurance-document-password upgrade pass");
            return;
        }
        try {
            List<Long> legacyIds = jdbcTemplate.queryForList(
                    "SELECT id FROM bikes WHERE insurance_document_password IS NOT NULL " +
                    "AND insurance_document_password NOT LIKE 'v1:%'", Long.class);
            if (legacyIds.isEmpty()) {
                return;
            }
            int upgraded = 0;
            for (Long id : legacyIds) {
                String plaintext = jdbcTemplate.queryForObject(
                        "SELECT insurance_document_password FROM bikes WHERE id = ?", String.class, id);
                if (plaintext == null) {
                    continue;
                }
                String ciphertext = encryptedStringConverter.convertToDatabaseColumn(plaintext);
                jdbcTemplate.update("UPDATE bikes SET insurance_document_password = ? WHERE id = ?", ciphertext, id);
                upgraded++;
            }
            log.info("Upgraded {} bike(s) insurance_document_password from plaintext to encrypted", upgraded);
        } catch (Exception ex) {
            log.warn("Insurance-document-password upgrade pass failed (non-fatal): {}", ex.getMessage());
        }
    }
}
