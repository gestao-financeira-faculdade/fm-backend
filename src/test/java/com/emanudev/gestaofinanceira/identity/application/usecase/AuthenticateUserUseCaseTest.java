package com.emanudev.gestaofinanceira.identity.application.usecase;

import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticateUserCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;
import com.emanudev.gestaofinanceira.identity.application.port.in.RefreshTokenCommand;
import com.emanudev.gestaofinanceira.identity.application.port.out.LoginAttemptPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort.ConsumeStatus;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort.Consumption;
import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.exception.InvalidCredentialsException;
import com.emanudev.gestaofinanceira.identity.domain.exception.TooManyLoginAttemptsException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-01T12:00:00Z");
    private static final String UNKNOWN_USER_HASH = "$2a$10$unknownUserDummyHash";
    private static final String CLIENT_IP = "203.0.113.7";

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;

    @Mock
    private TokenProviderPort tokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptPort loginAttempts;

    private AuthenticateUserUseCase authenticateUserUseCase;
    private RefreshTokenUseCase refreshTokenUseCase;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        when(passwordEncoder.encode(anyString())).thenReturn(UNKNOWN_USER_HASH);
        authenticateUserUseCase = new AuthenticateUserUseCase(
                userRepository,
                refreshTokenRepository,
                tokenProvider,
                passwordEncoder,
                loginAttempts,
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
    @DisplayName("Deve autenticar usuário ativo, emitir JWT da sessão e persistir o hash do refresh token na mesma sessão")
    void shouldAuthenticateValidActiveUserAndPersistRefreshTokenHash() {
        User admin = user(Role.ADMIN, "admin@gestaofinanceira.com", true);

        when(userRepository.findByEmail("admin@gestaofinanceira.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Admin@123", "$2a$10$hashedPassword")).thenReturn(true);
        when(tokenProvider.issueAccessToken(eq(admin), any(UUID.class))).thenReturn(
                new TokenProviderPort.IssueTokenResult("jwt-access-token", 900L, FIXED_NOW.plusSeconds(900))
        );
        when(tokenProvider.generateRefreshToken()).thenReturn("raw-refresh-token-value");
        when(tokenProvider.hashToken("raw-refresh-token-value")).thenReturn("hashed-refresh-token-sha256");

        AuthenticationResult result = authenticateUserUseCase.execute(
                new AuthenticateUserCommand("  ADMIN@gestaofinanceira.com ", "Admin@123", CLIENT_IP)
        );

        assertThat(result.accessToken()).isEqualTo("jwt-access-token");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token-value");
        assertThat(result.expiresIn()).isEqualTo(900L);
        assertThat(result.user().id()).isEqualTo(admin.id());

        ArgumentCaptor<UUID> sessionCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(tokenProvider).issueAccessToken(eq(admin), sessionCaptor.capture());
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.userId()).isEqualTo(admin.id());
        assertThat(savedToken.tokenHash()).isEqualTo("hashed-refresh-token-sha256");
        assertThat(savedToken.familyId()).isEqualTo(sessionCaptor.getValue());
        assertThat(savedToken.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(savedToken.expiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofDays(7)));
        assertThat(savedToken.revokedAt()).isNull();
        verify(loginAttempts).reset("admin@gestaofinanceira.com", CLIENT_IP);
    }

    @Test
    @DisplayName("Senha inválida retorna INVALID_CREDENTIALS e conta a falha para o bloqueio")
    void shouldRejectInvalidPassword() {
        User analista = user(Role.ANALISTA, "analista@gestaofinanceira.com", true);

        when(userRepository.findByEmail("analista@gestaofinanceira.com")).thenReturn(Optional.of(analista));
        when(passwordEncoder.matches("WrongPassword!", "$2a$10$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authenticateUserUseCase.execute(
                new AuthenticateUserCommand("analista@gestaofinanceira.com", "WrongPassword!", CLIENT_IP)))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> assertThat(((InvalidCredentialsException) ex).getErrorCode()).isEqualTo("INVALID_CREDENTIALS"));

        verify(loginAttempts).recordFailure("analista@gestaofinanceira.com", CLIENT_IP);
        verify(tokenProvider, never()).issueAccessToken(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Usuário inativo com senha errada recebe o mesmo INVALID_CREDENTIALS de qualquer senha errada")
    void shouldNotRevealInactiveAccountWhenPasswordIsWrong() {
        User inactiveUser = user(Role.AUDITOR, "inativo@gestaofinanceira.com", false);

        when(userRepository.findByEmail("inativo@gestaofinanceira.com")).thenReturn(Optional.of(inactiveUser));
        when(passwordEncoder.matches("senha-errada", "$2a$10$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authenticateUserUseCase.authenticate("inativo@gestaofinanceira.com", "senha-errada"))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> assertThat(((InvalidCredentialsException) ex).getErrorCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Usuário inativo só recebe USER_INACTIVE quando a senha está correta")
    void shouldRejectInactiveUserOnlyAfterPasswordMatches() {
        User inactiveUser = user(Role.AUDITOR, "inativo@gestaofinanceira.com", false);

        when(userRepository.findByEmail("inativo@gestaofinanceira.com")).thenReturn(Optional.of(inactiveUser));
        when(passwordEncoder.matches("Admin@123", "$2a$10$hashedPassword")).thenReturn(true);

        assertThatThrownBy(() -> authenticateUserUseCase.authenticate("inativo@gestaofinanceira.com", "Admin@123"))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> assertThat(((InvalidCredentialsException) ex).getErrorCode()).isEqualTo("USER_INACTIVE"));

        verify(tokenProvider, never()).issueAccessToken(any(), any());
    }

    @Test
    @DisplayName("E-mail inexistente ainda executa o BCrypt contra um hash fictício, para não revelar a conta pelo tempo de resposta")
    void shouldRunPasswordCheckForUnknownEmail() {
        when(userRepository.findByEmail("ninguem@gestaofinanceira.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticateUserUseCase.authenticate("ninguem@gestaofinanceira.com", "qualquer"))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> assertThat(((InvalidCredentialsException) ex).getErrorCode()).isEqualTo("INVALID_CREDENTIALS"));

        verify(passwordEncoder).matches("qualquer", UNKNOWN_USER_HASH);
        verify(loginAttempts).recordFailure("ninguem@gestaofinanceira.com", null);
    }

    @Test
    @DisplayName("Login bloqueado por excesso de falhas não consulta o usuário nem confere a senha")
    void shouldBlockLoginWhileLocked() {
        when(loginAttempts.lockRemaining("admin@gestaofinanceira.com", CLIENT_IP)).thenReturn(Optional.of(Duration.ofMinutes(12)));

        assertThatThrownBy(() -> authenticateUserUseCase.execute(
                new AuthenticateUserCommand("admin@gestaofinanceira.com", "Admin@123", CLIENT_IP)))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .satisfies(ex -> assertThat(((TooManyLoginAttemptsException) ex).getRetryAfter()).isEqualTo(Duration.ofMinutes(12)));

        verifyNoInteractions(userRepository);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Rotação consome o refresh atual e emite o novo par na mesma sessão")
    void shouldRotateRefreshTokenWithinSameSession() {
        User user = user(Role.AUDITOR, "auditor@gestaofinanceira.com", true);
        UUID sessionId = UUID.randomUUID();
        RefreshToken existingToken = refreshToken(user.id(), sessionId, null);

        when(tokenProvider.hashToken("old-raw-token")).thenReturn("old-token-hash");
        when(refreshTokenRepository.consume("old-token-hash", FIXED_NOW))
                .thenReturn(new Consumption(ConsumeStatus.CONSUMED, existingToken.revoke(FIXED_NOW)));
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(tokenProvider.issueAccessToken(user, sessionId)).thenReturn(
                new TokenProviderPort.IssueTokenResult("new-jwt-access-token", 900L, FIXED_NOW.plusSeconds(900))
        );
        when(tokenProvider.generateRefreshToken()).thenReturn("new-raw-refresh-token");
        when(tokenProvider.hashToken("new-raw-refresh-token")).thenReturn("new-token-hash");

        AuthenticationResult result = refreshTokenUseCase.execute(new RefreshTokenCommand("old-raw-token"));

        assertThat(result.accessToken()).isEqualTo("new-jwt-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-raw-refresh-token");

        ArgumentCaptor<RefreshToken> savedCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(savedCaptor.capture());
        RefreshToken createdNewToken = savedCaptor.getValue();
        assertThat(createdNewToken.tokenHash()).isEqualTo("new-token-hash");
        assertThat(createdNewToken.familyId()).isEqualTo(sessionId);
        assertThat(createdNewToken.isRevoked()).isFalse();
        assertThat(createdNewToken.expiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofDays(7)));
        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("Reuso de refresh já rotacionado revoga a sessão inteira e não emite tokens")
    void shouldRevokeWholeSessionWhenRotatedRefreshTokenIsReused() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RefreshToken rotatedToken = refreshToken(userId, sessionId, FIXED_NOW.minusSeconds(30));

        when(tokenProvider.hashToken("stolen-raw-token")).thenReturn("stolen-hash");
        when(refreshTokenRepository.consume("stolen-hash", FIXED_NOW))
                .thenReturn(new Consumption(ConsumeStatus.ALREADY_REVOKED, rotatedToken));

        assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshTokenCommand("stolen-raw-token")))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> assertThat(((InvalidCredentialsException) ex).getErrorCode()).isEqualTo("INVALID_REFRESH_TOKEN"));

        verify(refreshTokenRepository).revokeFamily(sessionId, FIXED_NOW);
        verify(tokenProvider, never()).issueAccessToken(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Refresh desconhecido retorna INVALID_REFRESH_TOKEN sem revogar nada")
    void shouldRejectUnknownRefreshToken() {
        when(tokenProvider.hashToken("unknown")).thenReturn("unknown-hash");
        when(refreshTokenRepository.consume("unknown-hash", FIXED_NOW)).thenReturn(Consumption.notFound());

        assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshTokenCommand("unknown")))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> assertThat(((InvalidCredentialsException) ex).getErrorCode()).isEqualTo("INVALID_REFRESH_TOKEN"));

        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    private static User user(Role role, String email, boolean active) {
        return new User(
                UUID.randomUUID(),
                "Usuário " + role.name(),
                email,
                "$2a$10$hashedPassword",
                role,
                active,
                FIXED_NOW.minusSeconds(86400),
                FIXED_NOW.minusSeconds(86400)
        );
    }

    private static RefreshToken refreshToken(UUID userId, UUID sessionId, Instant revokedAt) {
        return new RefreshToken(
                UUID.randomUUID(),
                userId,
                "old-token-hash",
                FIXED_NOW.plus(Duration.ofDays(5)),
                revokedAt,
                FIXED_NOW.minus(Duration.ofDays(2)),
                sessionId
        );
    }
}
