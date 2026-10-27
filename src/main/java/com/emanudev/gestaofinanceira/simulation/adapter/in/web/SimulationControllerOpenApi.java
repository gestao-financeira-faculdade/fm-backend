package com.emanudev.gestaofinanceira.simulation.adapter.in.web;

import com.emanudev.gestaofinanceira.simulation.adapter.in.web.dto.RunSimulationRequest;
import com.emanudev.gestaofinanceira.simulation.adapter.in.web.dto.SimulationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(
        name = "Simulação Estocástica de Risco (Monte Carlo HPC)",
        description = "Execução e consulta de simulações de Monte Carlo para projeção de fluxo de caixa, cálculo de Value at Risk (VaR 99%), mediana P50, probabilidade de ruína e bandas de percentis diárias."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/v1/simulations")
public interface SimulationControllerOpenApi {

    @Operation(
            summary = "Disparar nova simulação estocástica de Monte Carlo",
            description = "Executa até 500.000 cenários estocásticos para o horizonte configurado (30, 60 ou 90 dias) via motor gRPC C++/OpenCL ou fallback paralelo em JVM, persistindo o resultado e o Cone de Incerteza. Exclusivo para ADMIN."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Simulação concluída e persistida com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SimulationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acesso negado (exclusivo para ADMIN)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Parâmetros de cenário de estresse fora dos limites permitidos",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Motor de simulação indisponível ou limite de concorrência atingido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    ResponseEntity<SimulationResponse> runSimulation(
            @RequestBody(description = "Parâmetros opcionais do cenário de estresse (horizonte, número de cenários, inadimplência e choque de custos)", required = false)
            @org.springframework.web.bind.annotation.RequestBody(required = false) RunSimulationRequest request
    );

    @Operation(
            summary = "Consultar a última simulação de risco executada",
            description = "Retorna os indicadores de risco (VaR 99%, P50, probabilidade de ruína) e as bandas de percentis diárias da simulação mais recente."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Última simulação encontrada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SimulationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nenhuma simulação foi executada até o momento",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/latest")
    ResponseEntity<SimulationResponse> getLatestSimulation();

    @Operation(
            summary = "Consultar simulação de risco por ID",
            description = "Recupera os resultados completos e as bandas diárias de uma execução específica de simulação de Monte Carlo."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Simulação encontrada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SimulationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Simulação não encontrada para o UUID informado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    ResponseEntity<SimulationResponse> getSimulationById(
            @Parameter(description = "UUID da execução da simulação", required = true)
            @PathVariable UUID id
    );
}
