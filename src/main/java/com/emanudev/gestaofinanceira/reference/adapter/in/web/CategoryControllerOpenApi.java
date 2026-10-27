package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.CategoryController.CategoryResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.CategoryController.CreateCategoryRequest;
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
        name = "Categorias Financeiras",
        description = "Cadastro e consulta de categorias contábeis/financeiras (RECEITA/DESPESA), incluindo regras de obrigatoriedade de anexo fiscal e classificação de custo fixo/variável."
)
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/v1/categories")
public interface CategoryControllerOpenApi {

    @Operation(
            summary = "Listar todas as categorias financeiras ativas",
            description = "Retorna a lista de categorias financeiras disponíveis para classificação de lançamentos."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de categorias retornada com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = CategoryResponse.class))
                    )
            )
    })
    @GetMapping
    ResponseEntity<List<CategoryResponse>> listCategories();

    @Operation(
            summary = "Buscar categoria financeira por ID",
            description = "Retorna os detalhes de uma categoria financeira específica."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Categoria encontrada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CategoryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Categoria financeira não encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    ResponseEntity<CategoryResponse> getCategory(
            @Parameter(description = "UUID da categoria financeira", required = true)
            @PathVariable UUID id
    );

    @Operation(
            summary = "Criar nova categoria financeira",
            description = "Cadastra uma nova categoria de receita ou despesa. Operação permitida para ADMIN e ANALISTA."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Categoria financeira criada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CategoryResponse.class))
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
    ResponseEntity<CategoryResponse> createCategory(
            @RequestBody(description = "Dados da categoria financeira", required = true)
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateCategoryRequest request
    );
}
