package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.CounterpartyController.CounterpartyResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.CounterpartyController.CreateCounterpartyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(
        name = "Contrapartes (Clientes e Fornecedores)",
        description = "Cadastro e consulta de clientes, fornecedores e parceiros comerciais vinculados aos lançamentos financeiros."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/v1/counterparties")
public interface CounterpartyControllerOpenApi {

    @Operation(
            summary = "Listar todas as contrapartes cadastradas",
            description = "Retorna a lista de clientes e fornecedores ativos."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de contrapartes retornada com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = CounterpartyResponse.class))
                    )
            )
    })
    @GetMapping
    ResponseEntity<List<CounterpartyResponse>> listCounterparties();

    @Operation(
            summary = "Buscar contraparte por ID",
            description = "Retorna os dados cadastrais (razão social, nome fantasia, CPF/CNPJ e tipo) de uma contraparte específica."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Contraparte encontrada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CounterpartyResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Contraparte não encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    ResponseEntity<CounterpartyResponse> getCounterparty(
            @Parameter(description = "UUID da contraparte", required = true)
            @PathVariable UUID id
    );

    @Operation(
            summary = "Cadastrar nova contraparte",
            description = "Registra um novo cliente ou fornecedor. Operação permitida para ADMIN e ANALISTA."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Contraparte cadastrada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CounterpartyResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados de entrada inválidos",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acesso negado (requer papel ADMIN ou ANALISTA)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    ResponseEntity<CounterpartyResponse> createCounterparty(
            @RequestBody(description = "Dados cadastrais da contraparte", required = true)
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateCounterpartyRequest request
    );
}
