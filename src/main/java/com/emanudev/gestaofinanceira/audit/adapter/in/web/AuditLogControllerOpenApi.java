package com.emanudev.gestaofinanceira.audit.adapter.in.web;

import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogItemResponse;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(
        name = "Trilha de Auditoria e Governança",
        description = "Endpoints de consulta à trilha de auditoria imutável (append-only) de operações financeiras. Acesso restrito aos perfis ADMIN e AUDITOR."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/v1")
public interface AuditLogControllerOpenApi {

    @Operation(
            summary = "Consultar logs de auditoria com filtros e paginação",
            description = "Retorna registros paginados da trilha de auditoria filtrados por tipo de entidade, identificador da entidade, usuário ator, ação auditada e intervalo de datas."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Página de registros de auditoria retornada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PagedResult.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Parâmetros de paginação ou filtro inválidos",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Não autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acesso negado (requer papel ADMIN ou AUDITOR)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/audit-logs")
    ResponseEntity<PagedResult<AuditLogItemResponse>> listAuditLogs(
            @Parameter(description = "Tipo da entidade auditada (ex.: FINANCIAL_ENTRY, CATEGORY)", example = "FINANCIAL_ENTRY")
            @RequestParam(required = false) String entityType,
            @Parameter(description = "UUID da entidade auditada")
            @RequestParam(required = false) UUID entityId,
            @Parameter(description = "UUID do usuário que executou a ação")
            @RequestParam(required = false) UUID actorUserId,
            @Parameter(description = "Tipo de ação auditada (CREATE, SETTLE, CANCEL, UPLOAD_ATTACHMENT, etc.)")
            @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Timestamp inicial em UTC (ISO-8601)", example = "2026-01-01T00:00:00Z")
            @RequestParam(required = false) Instant startDate,
            @Parameter(description = "Timestamp final em UTC (ISO-8601)", example = "2026-12-31T23:59:59Z")
            @RequestParam(required = false) Instant endDate,
            @Parameter(description = "Índice da página (base 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página (máximo 100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "Consultar histórico completo de auditoria de um lançamento financeiro",
            description = "Retorna a linha do tempo cronológica de todas as mutações e snapshots JSON (before/after) de um lançamento específico."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Histórico de auditoria do lançamento retornado com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = AuditLogItemResponse.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Não autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acesso negado (requer papel ADMIN ou AUDITOR)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/entries/{entryId}/audit-history")
    ResponseEntity<List<AuditLogItemResponse>> getEntryAuditHistory(
            @Parameter(description = "UUID do lançamento financeiro", required = true)
            @PathVariable UUID entryId
    );
}
