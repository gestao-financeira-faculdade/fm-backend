package com.emanudev.gestaofinanceira.entry.adapter.in.web;

import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.FiscalAttachmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Tag(
        name = "Comprovantes e Anexos Fiscais",
        description = "Upload, listagem e download seguro de comprovantes fiscais (PDF, PNG, JPEG) armazenados no S3/MiniStack com validação de Magic Bytes e checksum SHA-256."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/v1/entries/{id}/attachments")
public interface AttachmentControllerOpenApi {

    @Operation(
            summary = "Anexar comprovante fiscal a um lançamento",
            description = "Realiza o upload multipart de um comprovante fiscal (máx. 10 MB), valida assinatura binária (Magic Bytes PDF/PNG/JPEG), calcula o hash SHA-256 e armazena o arquivo no bucket S3."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Comprovante fiscal armazenado e vinculado com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FiscalAttachmentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lançamento financeiro não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Formato de arquivo inválido (Magic Bytes não correspondem a PDF/PNG/JPEG) ou tamanho acima de 10 MB",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<FiscalAttachmentResponse> uploadAttachment(
            @Parameter(description = "UUID do lançamento financeiro", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Arquivo binário do comprovante fiscal (PDF, PNG ou JPEG, até 10 MB)", required = true)
            @RequestParam("file") MultipartFile file
    );

    @Operation(
            summary = "Listar comprovantes fiscais de um lançamento",
            description = "Retorna os metadados (nome original, tipo MIME, tamanho, hash SHA-256 e autor do upload) de todos os comprovantes vinculados ao lançamento."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de comprovantes retornada com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = FiscalAttachmentResponse.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lançamento financeiro não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping
    ResponseEntity<List<FiscalAttachmentResponse>> listAttachments(
            @Parameter(description = "UUID do lançamento financeiro", required = true)
            @PathVariable UUID id
    );

    @Operation(
            summary = "Baixar arquivo binário de um comprovante fiscal",
            description = "Recupera o conteúdo binário do comprovante fiscal armazenado no S3/MiniStack com cabeçalhos Content-Type e Content-Disposition originais."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Arquivo binário retornado com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lançamento ou comprovante fiscal não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{attachmentId}/download")
    ResponseEntity<byte[]> downloadAttachment(
            @Parameter(description = "UUID do lançamento financeiro", required = true)
            @PathVariable UUID id,
            @Parameter(description = "UUID do comprovante fiscal", required = true)
            @PathVariable UUID attachmentId
    );
}
