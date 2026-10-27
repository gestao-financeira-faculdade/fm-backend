package com.emanudev.gestaofinanceira.entry.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class FiscalAttachmentValidator {

    public static final long MAX_SIZE_BYTES = 10_485_760L; // 10 MB

    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_PNG = "image/png";

    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    public ValidatedAttachment validate(String originalFileName, byte[] fileBytes) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new InvalidAttachmentFormatException("Attachment original file name must not be blank");
        }
        if (fileBytes == null || fileBytes.length == 0) {
            throw new InvalidAttachmentFormatException("Attachment file must not be empty (0 bytes)");
        }
        if (fileBytes.length > MAX_SIZE_BYTES) {
            throw new InvalidAttachmentFormatException(
                    "Attachment file size (%d bytes) exceeds maximum allowed limit of 10 MB (%d bytes)"
                            .formatted(fileBytes.length, MAX_SIZE_BYTES)
            );
        }

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
        if (bytes == null || bytes.length < 3) {
            throw new InvalidAttachmentFormatException("File is too short to contain valid PDF, JPEG or PNG magic bytes");
        }
        if (startsWith(bytes, PDF_MAGIC)) {
            return MIME_PDF;
        }
        if (startsWith(bytes, JPEG_MAGIC)) {
            return MIME_JPEG;
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return MIME_PNG;
        }
        throw new InvalidAttachmentFormatException(
                "Unsupported or forged file binary signature: only genuine PDF, JPEG and PNG files are allowed"
        );
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

    private static boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
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
