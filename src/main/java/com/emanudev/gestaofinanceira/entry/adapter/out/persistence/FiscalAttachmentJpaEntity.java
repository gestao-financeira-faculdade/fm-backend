package com.emanudev.gestaofinanceira.entry.adapter.out.persistence;

import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fiscal_attachments")
public class FiscalAttachmentJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "storage_key", nullable = false, length = 500, unique = true)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "sha256_checksum", nullable = false, length = 64)
    private String sha256Checksum;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected FiscalAttachmentJpaEntity() {
    }

    public static FiscalAttachmentJpaEntity fromDomain(FiscalAttachment domain) {
        FiscalAttachmentJpaEntity entity = new FiscalAttachmentJpaEntity();
        entity.id = domain.id();
        entity.entryId = domain.entryId();
        entity.originalFileName = domain.originalFileName();
        entity.storageKey = domain.storageKey();
        entity.contentType = domain.contentType();
        entity.fileSizeBytes = domain.fileSizeBytes();
        entity.sha256Checksum = domain.sha256Checksum();
        entity.uploadedBy = domain.uploadedBy();
        entity.uploadedAt = domain.uploadedAt();
        return entity;
    }

    public FiscalAttachment toDomain() {
        return new FiscalAttachment(
                id,
                entryId,
                originalFileName,
                storageKey,
                contentType,
                fileSizeBytes,
                sha256Checksum,
                uploadedBy,
                uploadedAt
        );
    }
}
