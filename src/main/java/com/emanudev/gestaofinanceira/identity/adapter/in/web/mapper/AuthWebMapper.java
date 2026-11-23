package com.emanudev.gestaofinanceira.identity.adapter.in.web.mapper;

import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.LoginRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.LogoutRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.RefreshTokenRequest;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.TokenResponse;
import com.emanudev.gestaofinanceira.identity.adapter.in.web.dto.UserSummaryResponse;
import com.emanudev.gestaofinanceira.identity.adapter.out.security.JwtTokenProviderAdapter;
import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticateUserCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;
import com.emanudev.gestaofinanceira.identity.application.port.in.LogoutCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.RefreshTokenCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.UserProfileResult;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuthWebMapper {

    public AuthenticateUserCommand toAuthenticateCommand(LoginRequest request, String clientIp) {
        return new AuthenticateUserCommand(request.email(), request.password(), clientIp);
    }

    public RefreshTokenCommand toRefreshTokenCommand(RefreshTokenRequest request) {
        return new RefreshTokenCommand(request.refreshToken());
    }

    public LogoutCommand toLogoutCommand(LogoutRequest request, ActorContext actor, Jwt accessToken) {
        UUID userId = actor != null ? actor.userId() : null;
        String refreshToken = request != null ? request.refreshToken() : null;
        if (accessToken == null) {
            return new LogoutCommand(refreshToken, userId);
        }
        return new LogoutCommand(
                refreshToken,
                userId,
                parseUuid(accessToken.getClaimAsString(JwtTokenProviderAdapter.SESSION_ID_CLAIM)),
                accessToken.getId(),
                accessToken.getExpiresAt()
        );
    }

    public TokenResponse toTokenResponse(AuthenticationResult result) {
        return TokenResponse.from(result);
    }

    public UserSummaryResponse toUserSummaryResponse(UserProfileResult profile) {
        return UserSummaryResponse.from(profile);
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
