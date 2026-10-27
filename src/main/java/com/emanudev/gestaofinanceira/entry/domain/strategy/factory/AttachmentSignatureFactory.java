package com.emanudev.gestaofinanceira.entry.domain.strategy.factory;

import com.emanudev.gestaofinanceira.entry.domain.InvalidAttachmentFormatException;
import com.emanudev.gestaofinanceira.entry.domain.strategy.AttachmentSignatureStrategy;
import com.emanudev.gestaofinanceira.entry.domain.strategy.impl.JpegSignatureStrategy;
import com.emanudev.gestaofinanceira.entry.domain.strategy.impl.PdfSignatureStrategy;
import com.emanudev.gestaofinanceira.entry.domain.strategy.impl.PngSignatureStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AttachmentSignatureFactory {

    private final Map<String, AttachmentSignatureStrategy> strategies = new LinkedHashMap<>();

    @Autowired
    public AttachmentSignatureFactory(Set<AttachmentSignatureStrategy> strategySet) {
        strategySet.forEach(s -> strategies.put(s.getMimeType(), s));
    }

    public AttachmentSignatureFactory() {
        this(Set.copyOf(List.of(
                new PdfSignatureStrategy(),
                new JpegSignatureStrategy(),
                new PngSignatureStrategy()
        )));
    }

    public AttachmentSignatureStrategy getStrategy(String mimeType) {
        AttachmentSignatureStrategy strategy = strategies.get(mimeType);
        if (strategy == null) {
            throw new InvalidAttachmentFormatException("Unsupported attachment MIME type: " + mimeType);
        }
        return strategy;
    }

    public AttachmentSignatureStrategy getStrategy(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 3) {
            throw new InvalidAttachmentFormatException(
                    "File is too short to contain valid PDF, JPEG or PNG magic bytes"
            );
        }
        return strategies.values().stream()
                .filter(strategy -> strategy.matches(fileBytes))
                .findFirst()
                .orElseThrow(() -> new InvalidAttachmentFormatException(
                        "Unsupported or forged file binary signature: only genuine PDF, JPEG and PNG files are allowed"
                ));
    }
}
