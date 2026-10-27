package com.emanudev.gestaofinanceira.entry.domain;

import com.emanudev.gestaofinanceira.entry.domain.strategy.AttachmentValidationStrategy;
import com.emanudev.gestaofinanceira.entry.domain.strategy.factory.AttachmentSignatureFactory;
import com.emanudev.gestaofinanceira.entry.domain.strategy.impl.AttachmentFileNameValidationImpl;
import com.emanudev.gestaofinanceira.entry.domain.strategy.impl.AttachmentMaxSizeValidationImpl;
import com.emanudev.gestaofinanceira.entry.domain.strategy.impl.AttachmentNotEmptyValidationImpl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class FiscalAttachmentValidator {

    public static final long MAX_SIZE_BYTES = 10_485_760L; // 10 MB

    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_PNG = "image/png";

    private final List<AttachmentValidationStrategy> validationStrategies;
    private final AttachmentSignatureFactory signatureFactory;

    public FiscalAttachmentValidator(
            List<AttachmentValidationStrategy> validationStrategies,
            AttachmentSignatureFactory signatureFactory
    ) {
        this.validationStrategies = validationStrategies;
        this.signatureFactory = signatureFactory;
    }

    public FiscalAttachmentValidator() {
        this(
                List.of(
                        new AttachmentFileNameValidationImpl(),
                        new AttachmentNotEmptyValidationImpl(),
                        new AttachmentMaxSizeValidationImpl()
                ),
                new AttachmentSignatureFactory()
        );
    }

    public ValidatedAttachment validate(String originalFileName, byte[] fileBytes) {
        validationStrategies.forEach(validation -> validation.execute(originalFileName, fileBytes));

        String detectedMimeType = detectMimeTypeFromMagicBytes(fileBytes);
        String sanitizedFileName = sanitizeFileName(originalFileName);
        String sha256Checksum = computeSha256Hex(fileBytes);

        return new ValidatedAttachment(
                sanitizedFileName,
                detectedMimeType,
                fileBytes.length,
                sha256Checksum
        );
    }

    public String detectMimeTypeFromMagicBytes(byte[] bytes) {
        return signatureFactory.getStrategy(bytes).getMimeType();
    }

    public String computeSha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in JVM", e);
        }
    }

    private static String sanitizeFileName(String fileName) {
        String trimmed = fileName.trim().replace('\\', '/');
        int lastSlash = trimmed.lastIndexOf('/');
        String baseName = lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
        String cleaned = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            return "attachment";
        }
        return cleaned;
    }

    public record ValidatedAttachment(
            String sanitizedFileName,
            String detectedContentType,
            long fileSizeBytes,
            String sha256Checksum
    ) {
    }
}
