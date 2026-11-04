package com.emanudev.gestaofinanceira.entry.domain.strategy.impl;

import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachmentValidator;
import com.emanudev.gestaofinanceira.entry.domain.strategy.AttachmentSignatureStrategy;

public class PngSignatureStrategy implements AttachmentSignatureStrategy {

    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Override
    public String getMimeType() {
        return FiscalAttachmentValidator.MIME_PNG;
    }

    @Override
    public boolean matches(byte[] fileBytes) {
        return startsWith(fileBytes, PNG_MAGIC);
    }
}
