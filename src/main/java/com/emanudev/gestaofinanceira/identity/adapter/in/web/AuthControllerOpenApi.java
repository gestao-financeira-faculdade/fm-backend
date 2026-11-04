package com.emanudev.gestaofinanceira.identity.adapter.in.web;

import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.LoginRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.LogoutRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.RefreshTokenRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.TokenResponse;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(
        name = "Autenticação e Identidade",
        description = "Endpoints de autenticação stateless via JWT (HS256), rotação de Refresh Tokens no Redis e consulta do perfil autenticado."
)
@RequestMapping("/v1")
public interface AuthControllerOpenApi {

    @Operation(
            summary = "Autenticar usuário com e-mail e senha",
            description = "Valida as credenciais corporativas do usuário com BCrypt, emite um Access Token JWT (HS256) de curta duração e armazena um Refresh Token opaco no Redis."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Autenticação realizada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = TokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Payload de requisição inválido (campos obrigatórios ausentes ou formato de e-mail inválido)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Credenciais inválidas ou usuário inativo",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/auth/login")
    ResponseEntity<TokenResponse> login(
            @RequestBody(description = "Credenciais de acesso (e-mail e senha)", required = true)
            @Valid @org.springframework.web.bind.annotation.RequestBody LoginRequest request
    );

    @Operation(
            summary = "Renovar par de tokens via Refresh Token",
            description = "Valida o Refresh Token atual armazenado no Redis, aplica rotação atômica revogando o token anterior e emite um novo par de Access Token e Refresh Token."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tokens renovados com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = TokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Refresh Token ausente ou em branco",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Refresh Token inválido, expirado ou já revogado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/auth/refresh")
    ResponseEntity<TokenResponse> refresh(
            @RequestBody(description = "Refresh Token vigente para rotação", required = true)
            @Valid @org.springframework.web.bind.annotation.RequestBody RefreshTokenRequest request
    );

    @Operation(
            summary = "Encerrar sessão e revogar Refresh Tokens",
            description = "Revoga imediatamente no Redis o Refresh Token informado e/ou todos os tokens ativos pertencentes ao usuário autenticado.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sessão encerrada e tokens revogados com sucesso"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Token de autenticação ausente, inválido ou expirado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(
            @RequestBody(description = "Refresh Token opcional a ser revogado explicitamente", required = false)
            @org.springframework.web.bind.annotation.RequestBody(required = false) LogoutRequest request
    );

    @Operation(
            summary = "Consultar perfil do usuário autenticado",
            description = "Retorna os dados cadastrais e o papel RBAC (ADMIN, ANALISTA ou AUDITOR) do usuário associado ao Access Token atual.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Perfil do usuário retornado com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserSummaryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Token de autenticação ausente, inválido ou expirado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuário não encontrado na base de dados",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/users/me")
    ResponseEntity<UserSummaryResponse> me();
}
