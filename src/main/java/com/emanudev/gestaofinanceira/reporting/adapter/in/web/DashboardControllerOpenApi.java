package com.emanudev.gestaofinanceira.reporting.adapter.in.web;

import com.emanudev.gestaofinanceira.reporting.application.ExecutiveKpiResponse;
import com.emanudev.gestaofinanceira.reporting.application.UncertaintyConeResponse;
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
        name = "Dashboard Executivo e Cone de Incerteza",
        description = "Indicadores executivos em tempo real (Saldo Consolidado, Total a Receber/Pagar Hoje, Taxa de Inadimplência Mensal) e série combinada do Cone de Incerteza (histórico realizado + bandas de projeção estocástica)."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/v1/dashboard")
public interface DashboardControllerOpenApi {

    @Operation(
            summary = "Consultar KPIs executivos consolidados",
            description = "Calcula via jOOQ o Saldo Consolidado das contas ativas, Total a Receber Hoje, Total a Pagar Hoje e a Taxa de Inadimplência Mensal para a data de referência."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "KPIs executivos calculados com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ExecutiveKpiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Não autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/kpis")
    ResponseEntity<ExecutiveKpiResponse> getExecutiveKpis(
            @Parameter(description = "Data de referência opcional (ISO-8601, padrão: hoje em America/Sao_Paulo)", example = "2026-10-26")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate referenceDate
    );

    @Operation(
            summary = "Consultar série histórica e Cone de Incerteza",
            description = "Retorna a evolução histórica diária do saldo consolidado combinada com as bandas de percentis (P01, P05, P25, P50, P75, P95) da simulação de Monte Carlo selecionada ou mais recente."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Série do Cone de Incerteza retornada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UncertaintyConeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Não autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/uncertainty-cone")
    ResponseEntity<UncertaintyConeResponse> getUncertaintyCone(
            @Parameter(description = "Quantidade de dias de histórico a retornar (1 a 365, padrão: 30)", example = "30")
            @RequestParam(required = false) Integer historyDays,
            @Parameter(description = "UUID opcional de uma simulação específica (se omitido, utiliza a última simulação executada)")
            @RequestParam(required = false) UUID simulationId,
            @Parameter(description = "Data de referência opcional (ISO-8601)", example = "2026-10-26")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate referenceDate
    );
}
