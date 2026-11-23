package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import java.io.InputStream;

public record DownloadedAttachment(
        FiscalAttachment attachment,
        InputStream content
) {
}
