package com.emanudev.gestaofinanceira.identity.adapter.in.web.mapper;

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
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuthWebMapper {

    public AuthenticateUserCommand toAuthenticateCommand(LoginRequest request) {
        return new AuthenticateUserCommand(request.email(), request.password());
    }

    public RefreshTokenCommand toRefreshTokenCommand(RefreshTokenRequest request) {
        return new RefreshTokenCommand(request.refreshToken());
    }

    public LogoutCommand toLogoutCommand(LogoutRequest request, ActorContext actor) {
        UUID userId = actor != null ? actor.userId() : null;
        String refreshToken = request != null ? request.refreshToken() : null;
        return new LogoutCommand(refreshToken, userId);
    }

    public TokenResponse toTokenResponse(AuthenticationResult result) {
        return TokenResponse.from(result);
    }

    public UserSummaryResponse toUserSummaryResponse(UserProfileResult profile) {
        return UserSummaryResponse.from(profile);
    }
}
