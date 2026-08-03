package com.puduvandi.storage.service;

import com.puduvandi.storage.dto.InsuranceDetailsResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort extraction of the policy number and valid-till date from a two-wheeler
 * insurance PDF. There is no single Indian motor-insurance layout — ICICI Lombard, Bajaj
 * Allianz, HDFC Ergo, New India Assurance, Go Digit, Acko etc. all print these details
 * under different labels — so this matches a list of common label variants rather than
 * one fixed template. It is deliberately best-effort: callers must treat a null field as
 * "not found" and let the user fill/correct it manually, never as a hard failure.
 */
@Slf4j
@Service
public class InsuranceDocumentParser {

    // Ordered by specificity — the first one that matches wins. Covers the label
    // wording seen across most Indian motor insurers (policy/certificate/cover note).
    private static final List<Pattern> POLICY_NUMBER_PATTERNS = List.of(
            Pattern.compile("(?i)policy\\s*/?\\s*certificate\\s*(?:no\\.?|number)\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9/\\-]{4,29})"),
            Pattern.compile("(?i)certificate\\s*of\\s*insurance\\s*(?:no\\.?|number)\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9/\\-]{4,29})"),
            Pattern.compile("(?i)policy\\s*(?:no\\.?|number|num)\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9/\\-]{4,29})"),
            Pattern.compile("(?i)cover\\s*note\\s*(?:no\\.?|number)\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9/\\-]{4,29})")
    );

    // Date token: dd-mm-yyyy / dd/mm/yyyy / dd.mm.yyyy, "dd MMM yyyy" (e.g. 23 Jun 2026),
    // or ISO yyyy-mm-dd. Grouped so it can be dropped into any label pattern below.
    private static final String DATE_TOKEN =
            "(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|\\d{1,2}[-\\s][A-Za-z]{3,9}[-\\s]\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2})";

    // Direct "valid till/expiry" labels — these always refer to the end date we want.
    private static final List<Pattern> EXPLICIT_EXPIRY_PATTERNS = List.of(
            Pattern.compile("(?i)valid\\s*up\\s*to\\s*[:\\-]?\\s*" + DATE_TOKEN),
            Pattern.compile("(?i)valid\\s*till\\s*[:\\-]?\\s*" + DATE_TOKEN),
            Pattern.compile("(?i)valid\\s*to\\s*[:\\-]?\\s*" + DATE_TOKEN),
            Pattern.compile("(?i)(?:date\\s*of\\s*)?expiry(?:\\s*date)?\\s*[:\\-]?\\s*" + DATE_TOKEN),
            Pattern.compile("(?i)policy\\s*end\\s*date\\s*[:\\-]?\\s*" + DATE_TOKEN)
    );

    // "Period of Insurance"/"Policy Period" prints a From/To date range — the second
    // (To) date is the one we want, not the first.
    private static final Pattern PERIOD_RANGE_PATTERN =
            Pattern.compile("(?i)(?:period\\s*of\\s*insurance|policy\\s*period)[^0-9]{0,40}?" + DATE_TOKEN + "[^0-9]{1,15}?(?:to|-)[^0-9]{0,15}?" + DATE_TOKEN);

    // Locale.ENGLISH is pinned explicitly on every pattern below — without it,
    // ofPattern() resolves "MMM" against the JVM's default locale, and on a
    // server whose default is en_IN, September's short form is "Sept" (4
    // letters) rather than the "Sep" nearly every insurer actually prints,
    // silently failing to parse a real policy's expiry date in production.
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-M-yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    /**
     * @param password the PDF's user password, or null/blank if not yet known — an
     *                 empty password is tried first regardless, since most insurance
     *                 PDFs aren't actually encrypted and PDFBox requires an explicit
     *                 (even if empty) password argument to open those cleanly.
     */
    public InsuranceDetailsResponse extract(byte[] pdfBytes, String password) {
        String text;
        try (PDDocument document = Loader.loadPDF(pdfBytes, password != null ? password : "")) {
            text = new PDFTextStripper().getText(document);
        } catch (InvalidPasswordException ex) {
            return new InsuranceDetailsResponse(null, null, true);
        } catch (IOException ex) {
            log.warn("Could not parse insurance PDF for auto-fill: {}", ex.getMessage());
            return new InsuranceDetailsResponse(null, null, false);
        }

        return parseText(text);
    }

    /**
     * Strips PDF encryption so the file can be served/embedded (e.g. in an
     * &lt;iframe&gt;) without the viewer ever needing to know or prompt for the
     * password — the password is applied here, server-side, and never leaves
     * the backend. Returns the original bytes unchanged if the PDF isn't
     * actually encrypted or the given password doesn't unlock it, so callers
     * can always fall back to serving what they have.
     */
    public byte[] decrypt(byte[] pdfBytes, String password) {
        try (PDDocument document = Loader.loadPDF(pdfBytes, password != null ? password : "")) {
            if (!document.isEncrypted()) {
                return pdfBytes;
            }
            document.setAllSecurityToBeRemoved(true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            log.warn("Could not decrypt insurance PDF for inline viewing: {}", ex.getMessage());
            return pdfBytes;
        }
    }

    // Split out from extract() so the label/date regex logic can be exercised in
    // tests against raw strings, without needing an actual PDF file per case.
    InsuranceDetailsResponse parseText(String rawText) {
        // Collapse newlines/tabs/repeated spaces so labels and values split across
        // lines by the PDF layout still match a single-line regex.
        String normalized = rawText.replaceAll("\\s+", " ").trim();

        String policyNumber = findPolicyNumber(normalized);
        LocalDate validTill = findValidTill(normalized);

        return new InsuranceDetailsResponse(policyNumber, validTill, false);
    }

    private String findPolicyNumber(String text) {
        for (Pattern pattern : POLICY_NUMBER_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private LocalDate findValidTill(String text) {
        for (Pattern pattern : EXPLICIT_EXPIRY_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                LocalDate parsed = parseDate(matcher.group(1));
                if (parsed != null) return parsed;
            }
        }

        Matcher rangeMatcher = PERIOD_RANGE_PATTERN.matcher(text);
        if (rangeMatcher.find()) {
            // Group 2 is the "To" date of the From/To pair.
            LocalDate parsed = parseDate(rangeMatcher.group(2));
            if (parsed != null) return parsed;
        }

        return null;
    }

    private LocalDate parseDate(String raw) {
        String cleaned = raw.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleaned, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        log.debug("Found a date-like token in insurance PDF but couldn't parse it: {}", cleaned);
        return null;
    }
}
