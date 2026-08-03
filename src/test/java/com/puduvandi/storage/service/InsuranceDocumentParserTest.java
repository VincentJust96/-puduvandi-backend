package com.puduvandi.storage.service;

import com.puduvandi.storage.dto.InsuranceDetailsResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Different insurers lay these PDFs out completely differently, so this exercises
 * the label/date regex heuristics in InsuranceDocumentParser against representative
 * snippets rather than real PDFs (parseText() skips the PDFBox decoding step).
 */
class InsuranceDocumentParserTest {

    private final InsuranceDocumentParser parser = new InsuranceDocumentParser();

    @Test
    void readsPolicyNumberAndValidUptoLabel() {
        InsuranceDetailsResponse r = parser.parseText(
                "TWO WHEELER PACKAGE POLICY\nPolicy No: 3001/12345678/00/000\n" +
                "Insured Name: Ramesh Kumar\nValid upto: 23-06-2027\n");

        assertThat(r.policyNumber()).isEqualTo("3001/12345678/00/000");
        assertThat(r.validTill()).isEqualTo(LocalDate.of(2027, 6, 23));
    }

    @Test
    void readsPeriodOfInsuranceRangeAndTakesTheToDate() {
        InsuranceDetailsResponse r = parser.parseText(
                "Policy Number : OG-24-1234-5678-00001234\n" +
                "Period of Insurance From 15/06/2026 To 14/06/2027\n");

        assertThat(r.policyNumber()).isEqualTo("OG-24-1234-5678-00001234");
        assertThat(r.validTill()).isEqualTo(LocalDate.of(2027, 6, 14));
    }

    @Test
    void readsDdMmmYyyyDateFormatAndCertificateLabel() {
        InsuranceDetailsResponse r = parser.parseText(
                "Certificate of Insurance Number: 4056789012\n" +
                "Policy Expiry Date: 05 Sep 2026\n");

        assertThat(r.policyNumber()).isEqualTo("4056789012");
        assertThat(r.validTill()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void returnsNullsWhenNothingRecognizable() {
        InsuranceDetailsResponse r = parser.parseText(
                "This is a scanned image with no extractable text layer.");

        assertThat(r.policyNumber()).isNull();
        assertThat(r.validTill()).isNull();
        assertThat(r.passwordRequired()).isFalse();
    }

    @Test
    void flagsPasswordRequiredWhenNoneOrTheWrongOneIsGiven() throws IOException {
        byte[] encrypted = encryptedPdf("Policy No: 3001/12345678/00/000 Valid upto: 23-06-2027", "correct-horse");

        InsuranceDetailsResponse noPassword = parser.extract(encrypted, null);
        assertThat(noPassword.passwordRequired()).isTrue();
        assertThat(noPassword.policyNumber()).isNull();
        assertThat(noPassword.validTill()).isNull();

        InsuranceDetailsResponse wrongPassword = parser.extract(encrypted, "guess");
        assertThat(wrongPassword.passwordRequired()).isTrue();
    }

    @Test
    void extractsDetailsOncePasswordIsCorrect() throws IOException {
        byte[] encrypted = encryptedPdf("Policy No: 3001/12345678/00/000 Valid upto: 23-06-2027", "correct-horse");

        InsuranceDetailsResponse r = parser.extract(encrypted, "correct-horse");

        assertThat(r.passwordRequired()).isFalse();
        assertThat(r.policyNumber()).isEqualTo("3001/12345678/00/000");
        assertThat(r.validTill()).isEqualTo(LocalDate.of(2027, 6, 23));
    }

    @Test
    void decryptStripsEncryptionSoTheResultOpensWithoutAPassword() throws IOException {
        byte[] encrypted = encryptedPdf("Policy No: 3001/12345678/00/000 Valid upto: 23-06-2027", "correct-horse");

        byte[] decrypted = parser.decrypt(encrypted, "correct-horse");

        // extract() with a null password would report passwordRequired=true on the
        // still-encrypted original — confirming it comes back false here proves the
        // returned bytes are genuinely unlocked, not just a copy of the input.
        InsuranceDetailsResponse r = parser.extract(decrypted, null);
        assertThat(r.passwordRequired()).isFalse();
        assertThat(r.policyNumber()).isEqualTo("3001/12345678/00/000");
    }

    @Test
    void decryptReturnsOriginalBytesWhenNotEncrypted() throws IOException {
        byte[] plainPdf = plainPdf("Policy No: 3001/12345678/00/000");

        assertThat(parser.decrypt(plainPdf, "irrelevant")).isEqualTo(plainPdf);
    }

    @Test
    void decryptFallsBackToOriginalBytesOnInvalidInput() {
        byte[] notAPdf = "not a real pdf, just bytes".getBytes();

        assertThat(parser.decrypt(notAPdf, "irrelevant")).isEqualTo(notAPdf);
    }

    private byte[] plainPdf(String bodyText) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(bodyText);
                stream.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] encryptedPdf(String bodyText, String userPassword) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(bodyText);
                stream.endText();
            }

            AccessPermission permission = new AccessPermission();
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pw", userPassword, permission);
            document.protect(policy);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
