package com.emanudev.gestaofinanceira.entry.adapter.in.web;

import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.FiscalAttachmentResponse;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.mapper.AttachmentWebMapper;
import com.emanudev.gestaofinanceira.entry.application.DownloadedAttachment;
import com.emanudev.gestaofinanceira.entry.application.usecase.DownloadFiscalAttachmentUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.ListFiscalAttachmentsUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.UploadFiscalAttachmentUseCase;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AttachmentController implements AttachmentControllerOpenApi {

    private final UploadFiscalAttachmentUseCase uploadFiscalAttachmentUseCase;
    private final ListFiscalAttachmentsUseCase listFiscalAttachmentsUseCase;
    private final DownloadFiscalAttachmentUseCase downloadFiscalAttachmentUseCase;
    private final AttachmentWebMapper attachmentWebMapper;

    public AttachmentController(
            UploadFiscalAttachmentUseCase uploadFiscalAttachmentUseCase,
            ListFiscalAttachmentsUseCase listFiscalAttachmentsUseCase,
            DownloadFiscalAttachmentUseCase downloadFiscalAttachmentUseCase,
            AttachmentWebMapper attachmentWebMapper
    ) {
        this.uploadFiscalAttachmentUseCase = uploadFiscalAttachmentUseCase;
        this.listFiscalAttachmentsUseCase = listFiscalAttachmentsUseCase;
        this.downloadFiscalAttachmentUseCase = downloadFiscalAttachmentUseCase;
        this.attachmentWebMapper = attachmentWebMapper;
    }

    @Override
    public ResponseEntity<FiscalAttachmentResponse> uploadAttachment(UUID id, MultipartFile file) {
        byte[] bytes = attachmentWebMapper.extractBytes(file);
        String originalFilename = attachmentWebMapper.resolveFilename(file);
        FiscalAttachment saved = uploadFiscalAttachmentUseCase.execute(id, originalFilename, bytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(FiscalAttachmentResponse.fromDomain(saved));
    }

    @Override
    public ResponseEntity<List<FiscalAttachmentResponse>> listAttachments(UUID id) {
        List<FiscalAttachmentResponse> attachments = listFiscalAttachmentsUseCase.execute(id).stream()
                .map(FiscalAttachmentResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(attachments);
    }

    @Override
    public ResponseEntity<byte[]> downloadAttachment(UUID id, UUID attachmentId) {
        DownloadedAttachment downloaded = downloadFiscalAttachmentUseCase.execute(id, attachmentId);
        return attachmentWebMapper.toDownloadResponse(downloaded);
    }
}
