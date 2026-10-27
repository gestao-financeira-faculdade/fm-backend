package com.emanudev.gestaofinanceira.identity.adapter.in.web;

import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticateUserCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;
import com.emanudev.gestaofinanceira.identity.application.port.in.LogoutCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.RefreshTokenCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.UserProfileResult;
import com.emanudev.gestaofinanceira.identity.application.usecase.AuthenticateUserUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.GetCurrentUserUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.LogoutUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.RefreshTokenUseCase;
import com.emanudev.gestaofinanceira.identity.domain.exception.InvalidCredentialsException;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@RestController
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final ActorContextPort actorContextPort;

    public AuthController(
            AuthenticateUserUseCase authenticateUserUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            ActorContextPort actorContextPort
    ) {
        this.authenticateUserUseCase = Objects.requireNonNull(authenticateUserUseCase, "authenticateUserUseCase must not be null");
        this.refreshTokenUseCase = Objects.requireNonNull(refreshTokenUseCase, "refreshTokenUseCase must not be null");
        this.logoutUseCase = Objects.requireNonNull(logoutUseCase, "logoutUseCase must not be null");
        this.getCurrentUserUseCase = Objects.requireNonNull(getCurrentUserUseCase, "getCurrentUserUseCase must not be null");
        this.actorContextPort = Objects.requireNonNull(actorContextPort, "actorContextPort must not be null");
    }

    public record LoginRequest(
            @NotBlank(message = "O e-mail é obrigatório")
            @Email(message = "Formato de e-mail inválido")
            String email,
            @NotBlank(message = "A senha é obrigatória")
            String password
    ) {
    }

    public record RefreshTokenRequest(
            @NotBlank(message = "O refresh token é obrigatório")
            String refreshToken
    ) {
    }

    public record LogoutRequest(
            String refreshToken
    ) {
    }

    public record UserSummaryResponse(
            UUID id,
            String fullName,
            String email,
            String role,
            boolean active
    ) {
        public static UserSummaryResponse from(UserProfileResult profile) {
            return new UserSummaryResponse(
                    profile.id(),
                    profile.fullName(),
                    profile.email(),
                    profile.role().name(),
                    profile.active()
            );
        }
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserSummaryResponse user
    ) {
        public static TokenResponse from(AuthenticationResult result) {
            return new TokenResponse(
                    result.accessToken(),
                    result.refreshToken(),
                    result.expiresIn(),
                    UserSummaryResponse.from(result.user())
            );
        }
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationResult result = authenticateUserUseCase.execute(
                new AuthenticateUserCommand(request.email(), request.password())
        );
        return ResponseEntity.ok(TokenResponse.from(result));
    }

    @PostMapping("/api/v1/auth/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthenticationResult result = refreshTokenUseCase.execute(
                new RefreshTokenCommand(request.refreshToken())
        );
        return ResponseEntity.ok(TokenResponse.from(result));
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        ActorContext actor = actorContextPort.getCurrentActor();
        UUID userId = actor != null ? actor.userId() : null;
        String refreshToken = request != null ? request.refreshToken() : null;
        logoutUseCase.execute(new LogoutCommand(refreshToken, userId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/users/me")
    public ResponseEntity<UserSummaryResponse> me() {
        UserProfileResult profile = getCurrentUserUseCase.getCurrentUser();
        return ResponseEntity.ok(UserSummaryResponse.from(profile));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Unauthorized");
        problem.setType(URI.create("https://gestaofinanceira.com/errors/" + ex.getErrorCode().toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("error_code", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }
}
