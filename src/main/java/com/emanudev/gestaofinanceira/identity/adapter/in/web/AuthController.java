package com.emanudev.gestaofinanceira.identity.adapter.in.web;

import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.LoginRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.LogoutRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.RefreshTokenRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.TokenResponse;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.UserSummaryResponse;
import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticateUserCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;
import com.emanudev.gestaofinanceira.identity.application.port.in.LogoutCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.RefreshTokenCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.UserProfileResult;
import com.emanudev.gestaofinanceira.identity.application.usecase.AuthenticateUserUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.GetCurrentUserUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.LogoutUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.RefreshTokenUseCase;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
}
