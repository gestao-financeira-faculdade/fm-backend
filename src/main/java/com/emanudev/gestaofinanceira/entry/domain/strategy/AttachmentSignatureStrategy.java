package com.emanudev.gestaofinanceira.entry.domain.strategy;

public interface AttachmentSignatureStrategy {

    String getMimeType();

    boolean matches(byte[] fileBytes);

    default boolean startsWith(byte[] source, byte[] prefix) {
        if (source == null || source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
