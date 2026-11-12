package com.emanudev.gestaofinanceira.entry.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiscalAttachmentValidatorTest {

    private FiscalAttachmentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FiscalAttachmentValidator();
    }

    @Test
    @DisplayName("Should validate genuine PDF, JPEG and PNG files by magic bytes and compute SHA-256")
    void shouldValidateGenuinePdfJpegAndPng() {
        byte[] pdfBytes = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        FiscalAttachmentValidator.ValidatedAttachment pdfResult = validator.validate("nota_fiscal_01.pdf", pdfBytes);

        assertThat(pdfResult.detectedContentType()).isEqualTo(FiscalAttachmentValidator.MIME_PDF);
        assertThat(pdfResult.sanitizedFileName()).isEqualTo("nota_fiscal_01.pdf");
        assertThat(pdfResult.fileSizeBytes()).isEqualTo(pdfBytes.length);
        assertThat(pdfResult.sha256Checksum()).hasSize(64).matches("^[0-9a-f]{64}$");

        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
        FiscalAttachmentValidator.ValidatedAttachment jpegResult = validator.validate("comprovante.jpg", jpegBytes);
        assertThat(jpegResult.detectedContentType()).isEqualTo(FiscalAttachmentValidator.MIME_JPEG);

        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
        FiscalAttachmentValidator.ValidatedAttachment pngResult = validator.validate("recibo.png", pngBytes);
        assertThat(pngResult.detectedContentType()).isEqualTo(FiscalAttachmentValidator.MIME_PNG);
    }

    @Test
    @DisplayName("Should reject Windows PE .exe or script disguised as .pdf due to forged magic bytes")
    void shouldRejectDisguisedExeRenamedAsPdf() {
        // MZ header (Windows PE executable) disguised with .pdf extension
        byte[] fakePdfExeBytes = new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

        assertThatThrownBy(() -> validator.validate("comprovante_falso.pdf", fakePdfExeBytes))
                .isInstanceOf(InvalidAttachmentFormatException.class)
                .extracting(ex -> ((InvalidAttachmentFormatException) ex).getErrorCode())
                .isEqualTo(InvalidAttachmentFormatException.ERROR_CODE);
    }

    @Test
    @DisplayName("Should reject files exceeding 10 MB limit or empty 0-byte files")
    void shouldRejectOversizedOrEmptyFiles() {
        assertThatThrownBy(() -> validator.validate("empty.pdf", new byte[0]))
                .isInstanceOf(InvalidAttachmentFormatException.class);

        byte[] oversizedPdf = new byte[(int) FiscalAttachmentValidator.MAX_SIZE_BYTES + 1];
        oversizedPdf[0] = 0x25; // %
        oversizedPdf[1] = 0x50; // P
        oversizedPdf[2] = 0x44; // D
        oversizedPdf[3] = 0x46; // F
        oversizedPdf[4] = 0x2D; // -

        assertThatThrownBy(() -> validator.validate("huge_invoice.pdf", oversizedPdf))
                .isInstanceOf(InvalidAttachmentFormatException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    @DisplayName("Should sanitize path traversal characters in file name and produce deterministic SHA-256")
    void shouldSanitizeFileNameAndComputeDeterministicSha256() {
        byte[] pdfBytes = "%PDF-1.4\n%test-content".getBytes(StandardCharsets.US_ASCII);

        FiscalAttachmentValidator.ValidatedAttachment first = validator.validate("../../etc/passwd/nota fiscal#1.pdf", pdfBytes);
        FiscalAttachmentValidator.ValidatedAttachment second = validator.validate("nota_fiscal_1.pdf", pdfBytes);

        assertThat(first.sanitizedFileName()).isEqualTo("nota_fiscal_1.pdf");
        assertThat(first.sha256Checksum()).isEqualTo(second.sha256Checksum());
    }
}
