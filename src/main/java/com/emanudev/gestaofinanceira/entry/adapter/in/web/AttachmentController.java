package com.emanudev.gestaofinanceira.entry.adapter.in.web;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryUseCaseService;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryUseCaseService.DownloadedAttachment;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import com.emanudev.gestaofinanceira.entry.domain.InvalidAttachmentFormatException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AttachmentController implements AttachmentControllerOpenApi {

    private final FinancialEntryUseCaseService entryService;

    public AttachmentController(FinancialEntryUseCaseService entryService) {
        this.entryService = entryService;
    }

    @Override
    public ResponseEntity<FiscalAttachmentResponse> uploadAttachment(UUID id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAttachmentFormatException("Multipart file must not be null or empty");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read uploaded attachment bytes", ex);
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment";
        FiscalAttachment saved = entryService.uploadAttachment(id, originalFilename, bytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(FiscalAttachmentResponse.fromDomain(saved));
    }

    @Override
    public ResponseEntity<List<FiscalAttachmentResponse>> listAttachments(UUID id) {
        List<FiscalAttachmentResponse> attachments = entryService.listAttachments(id).stream()
                .map(FiscalAttachmentResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(attachments);
    }

    @Override
    public ResponseEntity<byte[]> downloadAttachment(UUID id, UUID attachmentId) {
        DownloadedAttachment downloaded = entryService.downloadAttachment(id, attachmentId);
        FiscalAttachment metadata = downloaded.attachment();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(metadata.contentType()));
        headers.setContentLength(downloaded.content().length);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(metadata.originalFileName()).build()
        );

        return new ResponseEntity<>(downloaded.content(), headers, HttpStatus.OK);
    }

    public record FiscalAttachmentResponse(
            UUID id,
            UUID entryId,
            String originalFileName,
            String contentType,
            long fileSizeBytes,
            String sha256Checksum,
            UUID uploadedBy,
            Instant uploadedAt
    ) {
        public static FiscalAttachmentResponse fromDomain(FiscalAttachment attachment) {
            return new FiscalAttachmentResponse(
                    attachment.id(),
                    attachment.entryId(),
                    attachment.originalFileName(),
                    attachment.contentType(),
                    attachment.fileSizeBytes(),
                    attachment.sha256Checksum(),
                    attachment.uploadedBy(),
                    attachment.uploadedAt()
            );
        }
    }
}
