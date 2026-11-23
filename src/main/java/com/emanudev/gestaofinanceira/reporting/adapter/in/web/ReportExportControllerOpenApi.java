package com.emanudev.gestaofinanceira.reporting.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(
        name = "Exportação de Relatórios Financeiros (CSV e PDF)",
        description = "Geração e download de relatórios analíticos de lançamentos financeiros em formato CSV (UTF-8 com BOM e delimitador ';') ou PDF."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/v1")
public interface ReportExportControllerOpenApi {

    @Operation(
            summary = "Exportar relatório de lançamentos via GET",
            description = "Gera e retorna o arquivo de relatório (CSV ou PDF) aplicando os filtros informados via query parameters."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Relatório gerado com sucesso",
                    content = {
                            @Content(mediaType = "text/csv;charset=UTF-8"),
                            @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Formato de exportação inválido (deve ser CSV ou PDF)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/reports/entries/export")
    ResponseEntity<byte[]> exportEntriesGet(
            @Parameter(description = "Formato do arquivo (CSV ou PDF)", example = "CSV")
            @RequestParam(defaultValue = "CSV") String format,
            @Parameter(description = "Filtro por tipo de lançamento (PAYABLE ou RECEIVABLE)")
            @RequestParam(required = false) String entryType,
            @Parameter(description = "Filtro por status (PENDING, OVERDUE, SETTLED, CANCELLED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filtro por UUID da categoria")
            @RequestParam(required = false) UUID categoryId,
            @Parameter(description = "Filtro por UUID da conta financeira")
            @RequestParam(required = false) UUID accountId,
            @Parameter(description = "Data de vencimento inicial (ISO-8601)", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @Parameter(description = "Data de vencimento final (ISO-8601)", example = "2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @Parameter(description = "Filtro textual na descrição")
            @RequestParam(required = false) String search
    );
}
