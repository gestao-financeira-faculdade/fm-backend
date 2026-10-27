package com.emanudev.gestaofinanceira.reporting.adapter.in.web;

import com.emanudev.gestaofinanceira.reporting.adapter.in.web.dto.ExportReportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

@Tag(
        name = "Reporting - Export",
        description = "Exportação de relatórios gerenciais e contábeis de lançamentos financeiros em formatos CSV (UTF-8 com BOM e proteção contra CSV Injection) e PDF."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/v1/reports")
public interface ReportExportControllerOpenApi {

    @Operation(
            summary = "Exportar lançamentos financeiros via GET",
            description = "Gera e faz o download do arquivo de relatório (CSV ou PDF) filtrando por período, status, tipo de fluxo e categoria."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Arquivo binário do relatório gerado com sucesso (text/csv ou application/pdf)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Formato de exportação inválido ou parâmetros de filtro inconsistentes",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping({"/entries/export", "/export"})
    ResponseEntity<byte[]> exportEntriesGet(
            @Parameter(description = "Formato do arquivo de saída (CSV ou PDF)", example = "CSV")
            @RequestParam(name = "format", defaultValue = "CSV") String format,
            @Parameter(description = "Data de vencimento inicial (ISO-8601)", example = "2026-01-01")
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Data de vencimento final (ISO-8601)", example = "2026-12-31")
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Filtro por status do lançamento", example = "SETTLED")
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Filtro por tipo do lançamento (PAYABLE ou RECEIVABLE)", example = "PAYABLE")
            @RequestParam(name = "type", required = false) String type,
            @Parameter(description = "Filtro por UUID de categoria")
            @RequestParam(name = "categoryId", required = false) UUID categoryId
    );

    @Operation(
            summary = "Exportar lançamentos financeiros via POST",
            description = "Permite solicitar a exportação de relatórios enviando os filtros via payload JSON ou combinados com query parameters."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Arquivo binário do relatório gerado com sucesso (text/csv ou application/pdf)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Formato de exportação inválido ou parâmetros de filtro inconsistentes",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping({"/entries/export", "/export"})
    ResponseEntity<byte[]> exportEntriesPost(
            @Parameter(description = "Formato do arquivo via query param (opcional se enviado no body)", example = "PDF")
            @RequestParam(name = "format", required = false) String queryFormat,
            @Parameter(description = "Data de vencimento inicial via query param")
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate queryStartDate,
            @Parameter(description = "Data de vencimento final via query param")
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate queryEndDate,
            @Parameter(description = "Status via query param")
            @RequestParam(name = "status", required = false) String queryStatus,
            @Parameter(description = "Tipo via query param")
            @RequestParam(name = "type", required = false) String queryType,
            @Parameter(description = "Categoria via query param")
            @RequestParam(name = "categoryId", required = false) UUID queryCategoryId,
            @RequestBody(required = false) ExportReportRequest body
    );
}
