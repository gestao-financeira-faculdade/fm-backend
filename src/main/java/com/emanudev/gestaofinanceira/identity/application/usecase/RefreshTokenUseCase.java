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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
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
    public AuthenticationResult execute(RefreshTokenCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return refresh(command.refreshToken());
    }

    @Transactional
    public AuthenticationResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token ausente ou inválido.");
        }

        Instant now = Instant.now(clock);
        String tokenHash = tokenProvider.hashToken(rawRefreshToken.trim());

        RefreshToken currentToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.atWarn()
                            .addKeyValue("event_type", "USER_REFRESH_FAILED")
                            .addKeyValue("reason", "TOKEN_NOT_FOUND")
                            .log("Refresh token not found");
                    return new InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token inválido ou expirado.");
                });

        if (currentToken.isRevoked() || currentToken.isExpired(now)) {
            log.atWarn()
                    .addKeyValue("event_type", "USER_REFRESH_FAILED")
                    .addKeyValue("user_id", currentToken.userId())
                    .addKeyValue("revoked", currentToken.isRevoked())
                    .addKeyValue("expired", currentToken.isExpired(now))
                    .log("Refresh token is revoked or expired");
            throw new InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Refresh token revogado ou expirado.");
        }

        User user = userRepository.findById(currentToken.userId())
                .orElseThrow(() -> new InvalidCredentialsException("INVALID_REFRESH_TOKEN", "Usuário associado ao token não encontrado."));

        if (!user.isActive()) {
            refreshTokenRepository.save(currentToken.revoke(now));
            throw new InvalidCredentialsException("USER_INACTIVE", "Conta de usuário inativa ou bloqueada.");
        }

        // Rotate refresh token: revoke the old one and issue a new pair
        RefreshToken revokedToken = currentToken.revoke(now);
        refreshTokenRepository.save(revokedToken);

        TokenProviderPort.IssueTokenResult issuedAccess = tokenProvider.issueAccessToken(user);
        String newRawRefreshToken = tokenProvider.generateRefreshToken();
        String newRefreshTokenHash = tokenProvider.hashToken(newRawRefreshToken);

        RefreshToken newRefreshToken = new RefreshToken(
                UUID.randomUUID(),
                user.id(),
                newRefreshTokenHash,
                now.plus(AuthenticateUserUseCase.REFRESH_TOKEN_TTL),
                null,
                now
        );
        refreshTokenRepository.save(newRefreshToken);

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
