package com.emanudev.gestaofinanceira.entry.application;

import java.util.UUID;

public interface AttachmentStoragePort {

    String store(UUID entryId, UUID attachmentId, String sanitizedFileName, byte[] content);

    byte[] load(String storageKey);
}
