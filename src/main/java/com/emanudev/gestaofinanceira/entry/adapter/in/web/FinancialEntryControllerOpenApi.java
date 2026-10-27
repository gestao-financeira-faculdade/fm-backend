package com.emanudev.gestaofinanceira.entry.adapter.in.web;

import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.CancelFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.CreateFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.FinancialEntryResponse;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.SettleFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.SettlementResponse;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryLineageNode;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntrySummaryView;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(
        name = "Lançamentos Financeiros (Contas a Pagar e a Receber)",
        description = "Gestão completa do ciclo de vida de lançamentos financeiros: criação idempotente, consulta paginada, baixa integral/parcial com desdobramento automático (split) e cancelamento lógico auditado."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/v1/entries")
public interface FinancialEntryControllerOpenApi {

    @Operation(
            summary = "Criar novo lançamento financeiro",
            description = "Registra uma obrigação (PAYABLE) ou direito (RECEIVABLE) com suporte a chave de idempotência via cabeçalho Idempotency-Key ou no corpo da requisição."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Lançamento financeiro criado com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FinancialEntryResponse.class))
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
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Violação de regra de negócio (ex.: valor menor ou igual a zero ou categoria inativa)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    ResponseEntity<FinancialEntryResponse> createEntry(
            @RequestBody(description = "Dados do lançamento financeiro a ser criado", required = true)
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateFinancialEntryRequest request,
            @Parameter(description = "Chave opcional de idempotência para evitar duplicidade em retentativas")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader
    );

    @Operation(
            summary = "Listar lançamentos financeiros com filtros e paginação",
            description = "Consulta lançamentos filtrados por tipo (PAYABLE/RECEIVABLE), status (PENDING/OVERDUE/SETTLED/CANCELLED), categoria, conta financeira, período de vencimento e busca textual."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Página de lançamentos retornada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PagedResult.class))
            )
    })
    @GetMapping
    ResponseEntity<PagedResult<FinancialEntrySummaryView>> listEntries(
            @Parameter(description = "Tipo do lançamento (PAYABLE ou RECEIVABLE)")
            @RequestParam(required = false) EntryType entryType,
            @Parameter(description = "Status do lançamento (PENDING, OVERDUE, SETTLED, CANCELLED)")
            @RequestParam(required = false) EntryStatus status,
            @Parameter(description = "UUID da categoria financeira")
            @RequestParam(required = false) UUID categoryId,
            @Parameter(description = "UUID da conta financeira")
            @RequestParam(required = false) UUID accountId,
            @Parameter(description = "Data de vencimento inicial (ISO-8601)", example = "2026-01-01")
            @RequestParam(required = false) LocalDate dueDateFrom,
            @Parameter(description = "Data de vencimento final (ISO-8601)", example = "2026-12-31")
            @RequestParam(required = false) LocalDate dueDateTo,
            @Parameter(description = "Termo de busca textual na descrição")
            @RequestParam(required = false) String search,
            @Parameter(description = "Índice da página (base 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página (máximo 100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "Buscar detalhes de um lançamento financeiro por ID",
            description = "Retorna todos os atributos e metadados agregados de um lançamento financeiro específico."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lançamento encontrado com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FinancialEntryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lançamento financeiro não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    ResponseEntity<FinancialEntryResponse> getEntry(
            @Parameter(description = "UUID do lançamento financeiro", required = true)
            @PathVariable UUID id
    );

    @Operation(
            summary = "Liquidar (dar baixa em) lançamento financeiro",
            description = "Realiza a liquidação total ou parcial de um lançamento PENDING ou OVERDUE. Quando o valor principal liquidado é menor que o original e createResidualEntryOnUnderpayment=true, gera automaticamente um lançamento filho residual (split). Exige anexo fiscal para valores acima de R$ 5.000,00 ou categorias com obrigatoriedade."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Baixa realizada com sucesso (incluindo eventual lançamento residual filho)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SettlementResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lançamento financeiro não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito de concorrência otimista (versão desatualizada)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Violação de regra de negócio (ex.: falta de anexo fiscal obrigatório ou transição de status inválida)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/{id}/settle")
    ResponseEntity<SettlementResponse> settleEntry(
            @Parameter(description = "UUID do lançamento financeiro", required = true)
            @PathVariable UUID id,
            @RequestBody(description = "Parâmetros da liquidação (valor, descontos, juros, multa e flag de resíduo)", required = false)
            @org.springframework.web.bind.annotation.RequestBody(required = false) SettleFinancialEntryRequest request
    );

    @Operation(
            summary = "Cancelar logicamente um lançamento financeiro",
            description = "Transiciona o lançamento para o estado terminal CANCELLED mediante justificativa obrigatória. Operação restrita ao perfil ADMIN."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lançamento cancelado com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FinancialEntryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acesso negado (exclusivo para ADMIN)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Justificativa ausente ou lançamento já liquidado/cancelado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/{id}/cancel")
    ResponseEntity<FinancialEntryResponse> cancelEntry(
            @Parameter(description = "UUID do lançamento financeiro", required = true)
            @PathVariable UUID id,
            @RequestBody(description = "Justificativa do cancelamento no corpo da requisição", required = false)
            @org.springframework.web.bind.annotation.RequestBody(required = false) CancelFinancialEntryRequest request,
            @Parameter(description = "Justificativa do cancelamento via query parameter (alternativa ao corpo)")
            @RequestParam(required = false) String reason
    );

    @Operation(
            summary = "Excluir logicamente (Soft Delete) um lançamento financeiro",
            description = "Atalho RESTful via DELETE para o cancelamento lógico auditado do lançamento financeiro. Operação restrita ao perfil ADMIN."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lançamento cancelado logicamente com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FinancialEntryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acesso negado (exclusivo para ADMIN)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @DeleteMapping("/{id}")
    ResponseEntity<FinancialEntryResponse> deleteEntryLogically(
            @Parameter(description = "UUID do lançamento financeiro", required = true)
            @PathVariable UUID id,
            @RequestBody(description = "Justificativa do cancelamento lógico", required = false)
            @org.springframework.web.bind.annotation.RequestBody(required = false) CancelFinancialEntryRequest request,
            @Parameter(description = "Justificativa do cancelamento via query parameter")
            @RequestParam(required = false) String reason
    );

    @Operation(
            summary = "Consultar árvore de desdobramentos (splits/resíduos) de um lançamento",
            description = "Retorna toda a linhagem hierárquica (lançamento raiz e filhos residuais gerados por baixas parciais) associada ao lançamento informado."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Árvore de linhagem retornada com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = FinancialEntryLineageNode.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lançamento financeiro não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}/splits")
    ResponseEntity<List<FinancialEntryLineageNode>> getEntrySplits(
            @Parameter(description = "UUID do lançamento financeiro", required = true)
            @PathVariable UUID id
    );
}
