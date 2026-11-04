package com.emanudev.gestaofinanceira.entry.adapter.in.web.mapper;

import com.emanudev.gestaofinanceira.entry.application.DownloadedAttachment;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import com.emanudev.gestaofinanceira.entry.domain.InvalidAttachmentFormatException;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AttachmentWebMapper {

    public byte[] extractBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAttachmentFormatException("Multipart file must not be null or empty");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read uploaded attachment bytes", ex);
        }
    }

    public String resolveFilename(MultipartFile file) {
        return Optional.ofNullable(file)
                .map(MultipartFile::getOriginalFilename)
                .filter(name -> !name.isBlank())
                .orElse("attachment");
    }

    public ResponseEntity<byte[]> toDownloadResponse(DownloadedAttachment downloaded) {
        FiscalAttachment metadata = downloaded.attachment();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(metadata.contentType()));
        headers.setContentLength(downloaded.content().length);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(metadata.originalFileName()).build()
        );
        return new ResponseEntity<>(downloaded.content(), headers, HttpStatus.OK);
    }
}
