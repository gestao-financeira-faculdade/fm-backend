package com.emanudev.gestaofinanceira.identity.application.usecase;

import com.emanudev.gestaofinanceira.identity.application.port.in.LogoutCommand;
import com.emanudev.gestaofinanceira.identity.application.port.out.AccessTokenBlocklistPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-01T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;

    @Mock
    private AccessTokenBlocklistPort accessTokenBlocklist;

    @Mock
    private TokenProviderPort tokenProvider;

    private LogoutUseCase logoutUseCase;

    @BeforeEach
    void setUp() {
        logoutUseCase = new LogoutUseCase(
                refreshTokenRepository,
                accessTokenBlocklist,
                tokenProvider,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("Logout invalida o access token atual até expirar e revoga só a sessão dele")
    void shouldBlockAccessTokenAndRevokeOnlyCurrentSession() {
        Instant accessExpiresAt = FIXED_NOW.plusSeconds(600);

        logoutUseCase.execute(new LogoutCommand(null, USER_ID, SESSION_ID, "jti-1", accessExpiresAt));

        verify(accessTokenBlocklist).block("jti-1", accessExpiresAt);
        verify(refreshTokenRepository).revokeFamily(SESSION_ID, FIXED_NOW);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    @DisplayName("Refresh token de outro usuário enviado no logout não é revogado")
    void shouldIgnoreRefreshTokenOwnedByAnotherUser() {
        UUID otherSession = UUID.randomUUID();
        when(tokenProvider.hashToken("foreign-raw")).thenReturn("foreign-hash");
        when(refreshTokenRepository.findByTokenHash("foreign-hash"))
                .thenReturn(Optional.of(refreshToken(UUID.randomUUID(), otherSession)));

        logoutUseCase.execute(new LogoutCommand("foreign-raw", USER_ID, SESSION_ID, "jti-1", FIXED_NOW.plusSeconds(60)));

        verify(refreshTokenRepository).revokeFamily(SESSION_ID, FIXED_NOW);
        verify(refreshTokenRepository, never()).revokeFamily(otherSession, FIXED_NOW);
    }

    @Test
    @DisplayName("Refresh token do próprio usuário enviado no logout revoga a sessão dele")
    void shouldRevokeSessionOfOwnRefreshToken() {
        UUID ownOtherSession = UUID.randomUUID();
        when(tokenProvider.hashToken("own-raw")).thenReturn("own-hash");
        when(refreshTokenRepository.findByTokenHash("own-hash"))
                .thenReturn(Optional.of(refreshToken(USER_ID, ownOtherSession)));

        logoutUseCase.execute(new LogoutCommand("own-raw", USER_ID));

        verify(refreshTokenRepository).revokeFamily(ownOtherSession, FIXED_NOW);
        verify(accessTokenBlocklist, never()).block(any(), any());
    }

    private static RefreshToken refreshToken(UUID userId, UUID sessionId) {
        return new RefreshToken(
                UUID.randomUUID(),
                userId,
                "hash",
                FIXED_NOW.plus(Duration.ofDays(7)),
                null,
                FIXED_NOW,
                sessionId
        );
    }
}
