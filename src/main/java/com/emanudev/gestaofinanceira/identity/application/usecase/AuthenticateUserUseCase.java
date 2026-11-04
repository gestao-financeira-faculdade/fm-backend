package com.emanudev.gestaofinanceira.identity.application.usecase;

import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticateUserCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;
import com.emanudev.gestaofinanceira.identity.application.port.in.UserProfileResult;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.exception.InvalidCredentialsException;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AuthenticateUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthenticateUserUseCase.class);
    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final UserRepositoryPort userRepository;
    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final TokenProviderPort tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AuthenticateUserUseCase(
            UserRepositoryPort userRepository,
            RefreshTokenRepositoryPort refreshTokenRepository,
            TokenProviderPort tokenProvider,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository must not be null");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public AuthenticationResult execute(@NotNull @Valid AuthenticateUserCommand command) {
        return authenticate(command.email(), command.password());
    }

    @Transactional
    public AuthenticationResult authenticate(@NotBlank @Email String email, @NotBlank String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.atWarn()
                            .addKeyValue("event_type", "USER_AUTH_FAILED")
                            .addKeyValue("reason", "USER_NOT_FOUND")
                            .log("Authentication failed for unknown email");
                    return new InvalidCredentialsException("Credenciais inválidas.");
                });

        if (!user.isActive()) {
            log.atWarn()
                    .addKeyValue("event_type", "USER_AUTH_FAILED")
                    .addKeyValue("user_id", user.id())
                    .addKeyValue("reason", "USER_INACTIVE")
                    .log("Authentication blocked for inactive user");
            throw new InvalidCredentialsException("USER_INACTIVE", "Conta de usuário inativa ou bloqueada.");
        }

        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            log.atWarn()
                    .addKeyValue("event_type", "USER_AUTH_FAILED")
                    .addKeyValue("user_id", user.id())
                    .addKeyValue("reason", "INVALID_PASSWORD")
                    .log("Authentication failed due to invalid password");
            throw new InvalidCredentialsException("Credenciais inválidas.");
        }

        Instant now = Instant.now(clock);
        TokenProviderPort.IssueTokenResult issuedAccess = tokenProvider.issueAccessToken(user);
        String rawRefreshToken = tokenProvider.generateRefreshToken();
        String refreshTokenHash = tokenProvider.hashToken(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                user.id(),
                refreshTokenHash,
                now.plus(REFRESH_TOKEN_TTL),
                null,
                now
        );
        refreshTokenRepository.save(refreshToken);

        log.atInfo()
                .addKeyValue("event_type", "USER_AUTHENTICATED")
                .addKeyValue("user_id", user.id())
                .addKeyValue("role", user.role().name())
                .log("User authenticated successfully");

        return new AuthenticationResult(
                issuedAccess.accessToken(),
                rawRefreshToken,
                issuedAccess.expiresInSeconds(),
                UserProfileResult.from(user)
        );
    }
}
