package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;

public record DownloadedAttachment(
        FiscalAttachment attachment,
        byte[] content
) {
}
