package com.emanudev.gestaofinanceira.identity.adapter.in.web;

import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.LoginRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.LogoutRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.RefreshTokenRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.TokenResponse;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.UserSummaryResponse;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.mapper.AuthWebMapper;
import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;
import com.emanudev.gestaofinanceira.identity.application.port.in.UserProfileResult;
import com.emanudev.gestaofinanceira.identity.application.usecase.AuthenticateUserUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.GetCurrentUserUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.LogoutUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.RefreshTokenUseCase;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthControllerOpenApi {

    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final ActorContextPort actorContextPort;
    private final AuthWebMapper authWebMapper;

    @Autowired
    public AuthController(
            AuthenticateUserUseCase authenticateUserUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            ActorContextPort actorContextPort,
            AuthWebMapper authWebMapper
    ) {
        this.authenticateUserUseCase = Objects.requireNonNull(authenticateUserUseCase, "authenticateUserUseCase must not be null");
        this.refreshTokenUseCase = Objects.requireNonNull(refreshTokenUseCase, "refreshTokenUseCase must not be null");
        this.logoutUseCase = Objects.requireNonNull(logoutUseCase, "logoutUseCase must not be null");
        this.getCurrentUserUseCase = Objects.requireNonNull(getCurrentUserUseCase, "getCurrentUserUseCase must not be null");
        this.actorContextPort = Objects.requireNonNull(actorContextPort, "actorContextPort must not be null");
        this.authWebMapper = Objects.requireNonNull(authWebMapper, "authWebMapper must not be null");
    }

    public AuthController(
            AuthenticateUserUseCase authenticateUserUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            ActorContextPort actorContextPort
    ) {
        this(
                authenticateUserUseCase,
                refreshTokenUseCase,
                logoutUseCase,
                getCurrentUserUseCase,
                actorContextPort,
                new AuthWebMapper()
        );
    }

    @Override
    public ResponseEntity<TokenResponse> login(LoginRequest request) {
        AuthenticationResult result = authenticateUserUseCase.execute(
                authWebMapper.toAuthenticateCommand(request)
        );
        return ResponseEntity.ok(authWebMapper.toTokenResponse(result));
    }

    @Override
    public ResponseEntity<TokenResponse> refresh(RefreshTokenRequest request) {
        AuthenticationResult result = refreshTokenUseCase.execute(
                authWebMapper.toRefreshTokenCommand(request)
        );
        return ResponseEntity.ok(authWebMapper.toTokenResponse(result));
    }

    @Override
    public ResponseEntity<Void> logout(LogoutRequest request) {
        ActorContext actor = actorContextPort.getCurrentActor();
        logoutUseCase.execute(authWebMapper.toLogoutCommand(request, actor));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<UserSummaryResponse> me() {
        UserProfileResult profile = getCurrentUserUseCase.getCurrentUser();
        return ResponseEntity.ok(authWebMapper.toUserSummaryResponse(profile));
    }
}
