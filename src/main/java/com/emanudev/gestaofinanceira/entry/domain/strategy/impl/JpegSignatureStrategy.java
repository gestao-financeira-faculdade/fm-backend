package com.emanudev.gestaofinanceira.entry.domain.strategy.impl;

import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachmentValidator;
import com.emanudev.gestaofinanceira.entry.domain.strategy.AttachmentSignatureStrategy;
import org.springframework.stereotype.Component;

@Component
public class JpegSignatureStrategy implements AttachmentSignatureStrategy {

    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    @Override
    public String getMimeType() {
        return FiscalAttachmentValidator.MIME_JPEG;
    }

    @Override
    public boolean matches(byte[] fileBytes) {
        return startsWith(fileBytes, JPEG_MAGIC);
    }
}
