package com.emanudev.gestaofinanceira.entry.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import java.time.Instant;
import java.util.UUID;

public record FiscalAttachmentResponse(
        UUID id,
        UUID entryId,
        String originalFileName,
        String contentType,
        long fileSizeBytes,
        String sha256Checksum,
        UUID uploadedBy,
        Instant uploadedAt
) {
    public static FiscalAttachmentResponse fromDomain(FiscalAttachment attachment) {
        return new FiscalAttachmentResponse(
                attachment.id(),
                attachment.entryId(),
                attachment.originalFileName(),
                attachment.contentType(),
                attachment.fileSizeBytes(),
                attachment.sha256Checksum(),
                attachment.uploadedBy(),
                attachment.uploadedAt()
        );
    }
}
