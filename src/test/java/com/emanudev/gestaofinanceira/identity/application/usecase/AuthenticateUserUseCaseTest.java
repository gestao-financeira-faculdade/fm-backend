package com.emanudev.gestaofinanceira.identity.application.usecase;

import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticateUserCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;
import com.emanudev.gestaofinanceira.identity.application.port.in.RefreshTokenCommand;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.exception.InvalidCredentialsException;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import com.emanudev.gestaofinanceira.identity.domain.model.Role;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-01T12:00:00Z");

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;

    @Mock
    private TokenProviderPort tokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    private Clock fixedClock;
    private AuthenticateUserUseCase authenticateUserUseCase;
    private RefreshTokenUseCase refreshTokenUseCase;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        authenticateUserUseCase = new AuthenticateUserUseCase(
                userRepository,
                refreshTokenRepository,
                tokenProvider,
                passwordEncoder,
                fixedClock
        );
        refreshTokenUseCase = new RefreshTokenUseCase(
                refreshTokenRepository,
                userRepository,
                tokenProvider,
                fixedClock
        );
    }

    @Test
    @DisplayName("Deve autenticar usuário ativo com credenciais válidas, emitir JWT de 15 min e persistir hash SHA-256 do refresh token")
    void shouldAuthenticateValidActiveUserAndPersistRefreshTokenHash() {
        UUID userId = UUID.fromString("0195f000-0000-7000-8000-000000000001");
        User admin = new User(
                userId,
                "Administrador Financeiro",
                "admin@gestaofinanceira.com",
                "$2a$10$hashedPassword",
                Role.ADMIN,
                true,
                FIXED_NOW.minusSeconds(86400),
                FIXED_NOW.minusSeconds(86400)
        );

        when(userRepository.findByEmail("admin@gestaofinanceira.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Admin@123", "$2a$10$hashedPassword")).thenReturn(true);
        when(tokenProvider.issueAccessToken(admin)).thenReturn(
                new TokenProviderPort.IssueTokenResult("jwt-access-token", 900L, FIXED_NOW.plusSeconds(900))
        );
        when(tokenProvider.generateRefreshToken()).thenReturn("raw-refresh-token-value");
        when(tokenProvider.hashToken("raw-refresh-token-value")).thenReturn("hashed-refresh-token-sha256");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationResult result = authenticateUserUseCase.execute(
                new AuthenticateUserCommand("  ADMIN@gestaofinanceira.com ", "Admin@123")
        );

        assertThat(result.accessToken()).isEqualTo("jwt-access-token");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token-value");
        assertThat(result.expiresIn()).isEqualTo(900L);
        assertThat(result.user().id()).isEqualTo(userId);
        assertThat(result.user().role()).isEqualTo(Role.ADMIN);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.userId()).isEqualTo(userId);
        assertThat(savedToken.tokenHash()).isEqualTo("hashed-refresh-token-sha256");
        assertThat(savedToken.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(savedToken.expiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofDays(7)));
        assertThat(savedToken.revokedAt()).isNull();
    }

    @Test
    @DisplayName("Deve rejeitar autenticação quando a senha informada for inválida")
    void shouldRejectInvalidPassword() {
        UUID userId = UUID.randomUUID();
        User analista = new User(
                userId,
                "Analista Financeiro",
                "analista@gestaofinanceira.com",
                "$2a$10$hashedPassword",
                Role.ANALISTA,
                true,
                FIXED_NOW,
                FIXED_NOW
        );

        when(userRepository.findByEmail("analista@gestaofinanceira.com")).thenReturn(Optional.of(analista));
        when(passwordEncoder.matches("WrongPassword!", "$2a$10$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authenticateUserUseCase.authenticate("analista@gestaofinanceira.com", "WrongPassword!"))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> assertThat(((InvalidCredentialsException) ex).getErrorCode()).isEqualTo("INVALID_CREDENTIALS"));

        verify(tokenProvider, never()).issueAccessToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve bloquear autenticação de usuário inativo")
    void shouldRejectInactiveUser() {
        UUID userId = UUID.randomUUID();
        User inactiveUser = new User(
                userId,
                "Usuário Inativo",
                "inativo@gestaofinanceira.com",
                "$2a$10$hashedPassword",
                Role.AUDITOR,
                false,
                FIXED_NOW,
                FIXED_NOW
        );

        when(userRepository.findByEmail("inativo@gestaofinanceira.com")).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> authenticateUserUseCase.authenticate("inativo@gestaofinanceira.com", "Admin@123"))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> assertThat(((InvalidCredentialsException) ex).getErrorCode()).isEqualTo("USER_INACTIVE"));

        verify(passwordEncoder, never()).matches(any(), any());
        verify(tokenProvider, never()).issueAccessToken(any());
    }

    @Test
    @DisplayName("Deve aplicar rotação de refresh token revogando o token anterior e emitindo novo par")
    void shouldRotateRefreshTokenAndRevokePreviousToken() {
        UUID userId = UUID.randomUUID();
        User user = new User(
                userId,
                "Auditor de Compliance",
                "auditor@gestaofinanceira.com",
                "$2a$10$hashedPassword",
                Role.AUDITOR,
                true,
                FIXED_NOW.minusSeconds(3600),
                FIXED_NOW.minusSeconds(3600)
        );

        RefreshToken existingToken = new RefreshToken(
                UUID.randomUUID(),
                userId,
                "old-token-hash",
                FIXED_NOW.plus(Duration.ofDays(5)),
                null,
                FIXED_NOW.minus(Duration.ofDays(2))
        );

        when(tokenProvider.hashToken("old-raw-token")).thenReturn("old-token-hash");
        when(refreshTokenRepository.findByTokenHash("old-token-hash")).thenReturn(Optional.of(existingToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenProvider.issueAccessToken(user)).thenReturn(
                new TokenProviderPort.IssueTokenResult("new-jwt-access-token", 900L, FIXED_NOW.plusSeconds(900))
        );
        when(tokenProvider.generateRefreshToken()).thenReturn("new-raw-refresh-token");
        when(tokenProvider.hashToken("new-raw-refresh-token")).thenReturn("new-token-hash");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationResult result = refreshTokenUseCase.execute(new RefreshTokenCommand("old-raw-token"));

        assertThat(result.accessToken()).isEqualTo("new-jwt-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-raw-refresh-token");
        assertThat(result.user().role()).isEqualTo(Role.AUDITOR);

        ArgumentCaptor<RefreshToken> savedCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(savedCaptor.capture());

        RefreshToken revokedOldToken = savedCaptor.getAllValues().get(0);
        RefreshToken createdNewToken = savedCaptor.getAllValues().get(1);

        assertThat(revokedOldToken.id()).isEqualTo(existingToken.id());
        assertThat(revokedOldToken.isRevoked()).isTrue();
        assertThat(revokedOldToken.revokedAt()).isEqualTo(FIXED_NOW);

        assertThat(createdNewToken.tokenHash()).isEqualTo("new-token-hash");
        assertThat(createdNewToken.isRevoked()).isFalse();
        assertThat(createdNewToken.expiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofDays(7)));
    }
}
