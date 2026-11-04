package com.emanudev.gestaofinanceira.entry.domain;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

import java.time.Instant;
import java.util.UUID;

public record FiscalAttachment(
        UUID id,
        UUID entryId,
        String originalFileName,
        String storageKey,
        String contentType,
        long fileSizeBytes,
        String sha256Checksum,
        UUID uploadedBy,
        Instant uploadedAt
) {

    public static final long MAX_FILE_SIZE_BYTES = 10_485_760L;

    public FiscalAttachment {
        if (id == null) {
            throw new BusinessRuleViolationException("INVALID_ATTACHMENT_ID", "Attachment id must not be null");
        }
        if (entryId == null) {
            throw new BusinessRuleViolationException("INVALID_ENTRY_ID", "Attachment entryId must not be null");
        }
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_ATTACHMENT_FILENAME", "Original file name must not be blank");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_STORAGE_KEY", "Storage key must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_CONTENT_TYPE", "Content type must not be blank");
        }
        if (fileSizeBytes <= 0 || fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new BusinessRuleViolationException(
                    "INVALID_ATTACHMENT_SIZE",
                    "Attachment size must be between 1 byte and 10 MB (10485760 bytes)"
            );
        }
        if (sha256Checksum == null || sha256Checksum.length() != 64) {
            throw new BusinessRuleViolationException("INVALID_CHECKSUM", "SHA-256 checksum must be 64 hex characters");
        }
        uploadedAt = uploadedAt != null ? uploadedAt : Instant.now();
    }
}
