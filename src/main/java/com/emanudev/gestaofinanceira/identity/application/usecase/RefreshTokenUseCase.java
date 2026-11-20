package com.emanudev.gestaofinanceira.identity.application.usecase;

import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;
import com.emanudev.gestaofinanceira.identity.application.port.in.RefreshTokenCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.UserProfileResult;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.exception.InvalidCredentialsException;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class RefreshTokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenUseCase.class);

    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final UserRepositoryPort userRepository;
    private final TokenProviderPort tokenProvider;
    private final Clock clock;

    public RefreshTokenUseCase(
            RefreshTokenRepositoryPort refreshTokenRepository,
            UserRepositoryPort userRepository,
            TokenProviderPort tokenProvider,
            Clock clock
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public AuthenticationResult execute(@NotNull @Valid RefreshTokenCommand command) {
        return refresh(command.refreshToken());
    }

    @Transactional
    public AuthenticationResult refresh(@NotBlank String rawRefreshToken) {
        Instant now = Instant.now(clock);
        String tokenHash = tokenProvider.hashToken(rawRefreshToken.trim());

        RefreshTokenRepositoryPort.Consumption consumption = refreshTokenRepository.consume(tokenHash, now);
        RefreshToken currentToken = consumption.token();

        switch (consumption.status()) {
            case NOT_FOUND -> {
                log.atWarn()
                        .addKeyValue("event_type", "USER_REFRESH_FAILED")
                        .addKeyValue("reason", "TOKEN_NOT_FOUND")
                        .log("Refresh token not found");
                throw new InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token inválido ou expirado.");
            }
            case ALREADY_REVOKED -> {
                refreshTokenRepository.revokeFamily(currentToken.familyId(), now);
                log.atWarn()
                        .addKeyValue("event_type", "USER_REFRESH_REUSE_DETECTED")
                        .addKeyValue("user_id", currentToken.userId())
                        .addKeyValue("session_id", currentToken.familyId())
                        .log("Revoked refresh token was reused; session revoked");
                throw new InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token revogado ou expirado.");
            }
            case CONSUMED -> {
            }
        }

        if (currentToken.isExpired(now)) {
            log.atWarn()
                    .addKeyValue("event_type", "USER_REFRESH_FAILED")
                    .addKeyValue("user_id", currentToken.userId())
                    .addKeyValue("reason", "TOKEN_EXPIRED")
                    .log("Refresh token is expired");
            throw new InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token revogado ou expirado.");
        }

        User user = userRepository.findById(currentToken.userId())
                .orElseThrow(() -> new InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Usuário associado ao token não encontrado."));

        if (!user.isActive()) {
            refreshTokenRepository.revokeFamily(currentToken.familyId(), now);
            throw new InvalidCredentialsException("USER_INACTIVE", "Conta de usuário inativa ou bloqueada.");
        }

        UUID sessionId = currentToken.familyId();
        TokenProviderPort.IssueTokenResult issuedAccess = tokenProvider.issueAccessToken(user, sessionId);
        String newRawRefreshToken = tokenProvider.generateRefreshToken();
        String newRefreshTokenHash = tokenProvider.hashToken(newRawRefreshToken);

        refreshTokenRepository.save(RefreshToken.issue(
                user.id(),
                newRefreshTokenHash,
                now,
                now.plus(AuthenticateUserUseCase.REFRESH_TOKEN_TTL),
                sessionId
        ));

        log.atInfo()
                .addKeyValue("event_type", "USER_TOKEN_REFRESHED")
                .addKeyValue("user_id", user.id())
                .log("Refresh token rotated successfully");

        return new AuthenticationResult(
                issuedAccess.accessToken(),
                newRawRefreshToken,
                issuedAccess.expiresInSeconds(),
                UserProfileResult.from(user)
        );
    }
}
