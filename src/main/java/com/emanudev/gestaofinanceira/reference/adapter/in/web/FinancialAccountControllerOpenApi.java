package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.FinancialAccountController.CreateFinancialAccountRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.FinancialAccountController.FinancialAccountResponse;
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
        name = "Contas Financeiras e Bancárias",
        description = "Cadastro e consulta de contas bancárias, caixas e carteiras corporativas utilizadas para liquidação e consolidação de saldo."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping({"/v1/accounts", "/v1/financial-accounts"})
public interface FinancialAccountControllerOpenApi {

    @Operation(
            summary = "Listar todas as contas financeiras ativas",
            description = "Retorna todas as contas financeiras cadastradas e seus respectivos saldos iniciais."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de contas financeiras retornada com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = FinancialAccountResponse.class))
                    )
            )
    })
    @GetMapping
    ResponseEntity<List<FinancialAccountResponse>> listAccounts();

    @Operation(
            summary = "Buscar conta financeira por ID",
            description = "Retorna os detalhes de uma conta financeira específica."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Conta financeira encontrada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FinancialAccountResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Conta financeira não encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    ResponseEntity<FinancialAccountResponse> getAccount(
            @Parameter(description = "UUID da conta financeira", required = true)
            @PathVariable UUID id
    );

    @Operation(
            summary = "Criar nova conta financeira",
            description = "Cadastra uma nova conta bancária, caixa ou conta de investimento. Operação permitida para ADMIN e ANALISTA."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Conta financeira criada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FinancialAccountResponse.class))
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
    ResponseEntity<FinancialAccountResponse> createAccount(
            @RequestBody(description = "Dados para abertura/cadastro da conta financeira", required = true)
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateFinancialAccountRequest request
    );
}
