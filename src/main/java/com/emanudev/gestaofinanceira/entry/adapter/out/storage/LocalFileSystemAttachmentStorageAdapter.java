package com.emanudev.gestaofinanceira.entry.adapter.out.storage;

import com.emanudev.gestaofinanceira.entry.application.AttachmentStoragePort;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class LocalFileSystemAttachmentStorageAdapter implements AttachmentStoragePort {

    private final Path baseDirectory;

    public LocalFileSystemAttachmentStorageAdapter(
            @Value("${app.storage.attachments-dir:./target/attachments-storage}") String attachmentsDir
    ) {
        this.baseDirectory = Paths.get(attachmentsDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID entryId, UUID attachmentId, String sanitizedFileName, byte[] content) {
        String storageKey = entryId + "/" + attachmentId + "_" + sanitizedFileName;
        Path targetPath = resolveSafePath(storageKey);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content);
            return storageKey;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store fiscal attachment on disk: " + storageKey, ex);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        Path targetPath = resolveSafePath(storageKey);
        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new ResourceNotFoundException("FiscalAttachmentFile", storageKey);
        }
        try {
            return Files.readAllBytes(targetPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read fiscal attachment from disk: " + storageKey, ex);
        }
    }

    private Path resolveSafePath(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_STORAGE_KEY", "Storage key must not be blank");
        }
        Path resolved = baseDirectory.resolve(storageKey).normalize();
        if (!resolved.startsWith(baseDirectory)) {
            throw new BusinessRuleViolationException("INVALID_STORAGE_KEY", "Path traversal attempt detected in storage key");
        }
        return resolved;
    }
}
