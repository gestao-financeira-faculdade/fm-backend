package com.emanudev.gestaofinanceira.identity.application.usecase;

import com.emanudev.gestaofinanceira.identity.application.port.in.LogoutCommand;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class LogoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(LogoutUseCase.class);

    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final TokenProviderPort tokenProvider;
    private final Clock clock;

    public LogoutUseCase(
            RefreshTokenRepositoryPort refreshTokenRepository,
            TokenProviderPort tokenProvider,
            Clock clock
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository must not be null");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public void execute(LogoutCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = Instant.now(clock);

        if (command.refreshToken() != null && !command.refreshToken().isBlank()) {
            String tokenHash = tokenProvider.hashToken(command.refreshToken().trim());
            refreshTokenRepository.findByTokenHash(tokenHash)
                    .ifPresent(token -> refreshTokenRepository.save(token.revoke(now)));
        }

        if (command.userId() != null) {
            refreshTokenRepository.revokeAllByUserId(command.userId(), now);
        }

        log.atInfo()
                .addKeyValue("event_type", "USER_LOGGED_OUT")
                .addKeyValue("user_id", command.userId())
                .log("User session refresh tokens revoked");
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        execute(new LogoutCommand(rawRefreshToken, null));
    }

    @Transactional
    public void logoutAllForUser(UUID userId) {
        execute(new LogoutCommand(null, userId));
    }
}
