package com.emanudev.gestaofinanceira.entry.adapter.in.web;

import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.FiscalAttachmentResponse;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.mapper.AttachmentWebMapper;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryUseCaseService;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryUseCaseService.DownloadedAttachment;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AttachmentController implements AttachmentControllerOpenApi {

    private final FinancialEntryUseCaseService entryService;
    private final AttachmentWebMapper attachmentWebMapper;

    @Autowired
    public AttachmentController(
            FinancialEntryUseCaseService entryService,
            AttachmentWebMapper attachmentWebMapper
    ) {
        this.entryService = entryService;
        this.attachmentWebMapper = attachmentWebMapper;
    }

    public AttachmentController(FinancialEntryUseCaseService entryService) {
        this(entryService, new AttachmentWebMapper());
    }

    @Override
    public ResponseEntity<FiscalAttachmentResponse> uploadAttachment(UUID id, MultipartFile file) {
        byte[] bytes = attachmentWebMapper.extractBytes(file);
        String originalFilename = attachmentWebMapper.resolveFilename(file);
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
        return attachmentWebMapper.toDownloadResponse(downloaded);
    }
}
