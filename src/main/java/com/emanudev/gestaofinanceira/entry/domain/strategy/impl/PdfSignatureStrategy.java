package com.emanudev.gestaofinanceira.entry.domain.strategy.impl;

import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachmentValidator;
import com.emanudev.gestaofinanceira.entry.domain.strategy.AttachmentSignatureStrategy;
import org.springframework.stereotype.Component;

@Component
public class PdfSignatureStrategy implements AttachmentSignatureStrategy {

    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-

    @Override
    public String getMimeType() {
        return FiscalAttachmentValidator.MIME_PDF;
    }

    @Override
    public boolean matches(byte[] fileBytes) {
        return startsWith(fileBytes, PDF_MAGIC);
    }
}
