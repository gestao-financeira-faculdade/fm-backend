package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateFinancialAccountRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.FinancialAccountResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.PatchFinancialAccountRequest;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(
        name = "Contas Financeiras e Bancárias",
        description = "Cadastro e consulta de contas bancárias, caixas e carteiras corporativas utilizadas para liquidação e consolidação de saldo."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/v1/accounts")
public interface FinancialAccountControllerOpenApi {

    @Operation(
            summary = "Listar contas financeiras com paginação",
            description = "Retorna uma página de contas financeiras ordenada por nome. Por padrão só as ativas; includeInactive=true inclui as desativadas."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Página retornada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PagedResult.class))
            )
    })
    @GetMapping
    ResponseEntity<PagedResult<FinancialAccountResponse>> listAccounts(
            @Parameter(description = "Índice da página (base 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página (máximo 500)", example = "50")
            @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Inclui registros desativados")
            @RequestParam(defaultValue = "false") boolean includeInactive
    );

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
            description = "Cadastra uma nova conta bancária, caixa ou conta de investimento. Operação permitida apenas para ADMIN."
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
                    description = "Acesso negado (requer papel ADMIN)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    ResponseEntity<FinancialAccountResponse> createAccount(
            @RequestBody(description = "Dados para abertura/cadastro da conta financeira", required = true)
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateFinancialAccountRequest request
    );

    @Operation(
            summary = "Alterar conta financeira",
            description = "Atualiza parcialmente o cadastro: só os campos enviados mudam. active=true reativa um registro desativado."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cadastro atualizado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FinancialAccountResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Registro não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Nome duplicado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PatchMapping("/{id}")
    ResponseEntity<FinancialAccountResponse> patchFinancialAccount(
            @Parameter(description = "UUID do registro", required = true)
            @PathVariable UUID id,
            @RequestBody(description = "Campos a alterar", required = true)
            @Valid @org.springframework.web.bind.annotation.RequestBody PatchFinancialAccountRequest request
    );

    @Operation(
            summary = "Desativar conta financeira",
            description = "Desativa o registro (exclusão lógica). Idempotente: desativar de novo responde 204."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Registro desativado"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Registro não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deactivateFinancialAccount(
            @Parameter(description = "UUID do registro", required = true)
            @PathVariable UUID id
    );
}
