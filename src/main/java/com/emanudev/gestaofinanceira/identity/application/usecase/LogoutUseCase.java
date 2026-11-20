package com.emanudev.gestaofinanceira.identity.application.usecase;

import com.emanudev.gestaofinanceira.identity.application.port.in.LogoutCommand;
import com.emanudev.gestaofinanceira.identity.application.port.out.AccessTokenBlocklistPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import jakarta.validation.Valid;
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
    private final AccessTokenBlocklistPort accessTokenBlocklist;
    private final TokenProviderPort tokenProvider;
    private final Clock clock;

    public LogoutUseCase(
            RefreshTokenRepositoryPort refreshTokenRepository,
            AccessTokenBlocklistPort accessTokenBlocklist,
            TokenProviderPort tokenProvider,
            Clock clock
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository must not be null");
        this.accessTokenBlocklist = Objects.requireNonNull(accessTokenBlocklist, "accessTokenBlocklist must not be null");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public void execute(@NotNull @Valid LogoutCommand command) {
        Instant now = Instant.now(clock);

        if (command.accessTokenId() != null && command.accessTokenExpiresAt() != null) {
            accessTokenBlocklist.block(command.accessTokenId(), command.accessTokenExpiresAt());
        }

        Optional.ofNullable(command.sessionId())
                .ifPresent(sessionId -> refreshTokenRepository.revokeFamily(sessionId, now));

        Optional.ofNullable(command.refreshToken())
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(tokenProvider::hashToken)
                .flatMap(refreshTokenRepository::findByTokenHash)
                .ifPresent(token -> revokeIfOwned(token, command.userId(), now));

        log.atInfo()
                .addKeyValue("event_type", "USER_LOGGED_OUT")
                .addKeyValue("user_id", command.userId())
                .addKeyValue("session_id", command.sessionId())
                .log("User session revoked");
    }

    @Transactional
    public void logoutAllForUser(@NotNull UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now(clock));
    }

    private void revokeIfOwned(RefreshToken token, UUID userId, Instant now) {
        if (!token.userId().equals(userId)) {
            log.atWarn()
                    .addKeyValue("event_type", "USER_LOGOUT_FOREIGN_TOKEN")
                    .addKeyValue("user_id", userId)
                    .log("Logout ignored a refresh token owned by another user");
            return;
        }
        refreshTokenRepository.revokeFamily(token.familyId(), now);
    }
}
