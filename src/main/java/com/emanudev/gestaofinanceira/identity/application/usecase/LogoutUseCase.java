package com.emanudev.gestaofinanceira.identity.application.usecase;

import com.emanudev.gestaofinanceira.identity.application.port.in.LogoutCommand;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
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
    public void execute(@NotNull @Valid LogoutCommand command) {
        Instant now = Instant.now(clock);

        Optional.ofNullable(command.refreshToken())
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(tokenProvider::hashToken)
                .flatMap(refreshTokenRepository::findByTokenHash)
                .ifPresent(token -> refreshTokenRepository.save(token.revoke(now)));

        Optional.ofNullable(command.userId())
                .ifPresent(userId -> refreshTokenRepository.revokeAllByUserId(userId, now));

        log.atInfo()
                .addKeyValue("event_type", "USER_LOGGED_OUT")
                .addKeyValue("user_id", command.userId())
                .log("User session refresh tokens revoked");
    }

    @Transactional
    public void logout(@NotBlank String rawRefreshToken) {
        execute(new LogoutCommand(rawRefreshToken, null));
    }

    @Transactional
    public void logoutAllForUser(@NotNull UUID userId) {
        execute(new LogoutCommand(null, userId));
    }
}
