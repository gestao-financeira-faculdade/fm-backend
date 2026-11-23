package com.emanudev.gestaofinanceira.identity.application.usecase;

import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticateUserCommand;
import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;
import com.emanudev.gestaofinanceira.identity.application.port.in.UserProfileResult;
import com.emanudev.gestaofinanceira.identity.application.port.out.LoginAttemptPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.exception.InvalidCredentialsException;
import com.emanudev.gestaofinanceira.identity.domain.exception.TooManyLoginAttemptsException;
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
import java.util.Optional;
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
    private final LoginAttemptPort loginAttempts;
    private final Clock clock;
    private final String unknownUserPasswordHash;

    public AuthenticateUserUseCase(
            UserRepositoryPort userRepository,
            RefreshTokenRepositoryPort refreshTokenRepository,
            TokenProviderPort tokenProvider,
            PasswordEncoder passwordEncoder,
            LoginAttemptPort loginAttempts,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository must not be null");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.loginAttempts = Objects.requireNonNull(loginAttempts, "loginAttempts must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.unknownUserPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthenticationResult execute(@NotNull @Valid AuthenticateUserCommand command) {
        return authenticate(command.email(), command.password(), command.clientIp());
    }

    @Transactional
    public AuthenticationResult authenticate(@NotBlank @Email String email, @NotBlank String rawPassword) {
        return authenticate(email, rawPassword, null);
    }

    private AuthenticationResult authenticate(String email, String rawPassword, String clientIp) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        loginAttempts.lockRemaining(normalizedEmail, clientIp).ifPresent(retryAfter -> {
            log.atWarn()
                    .addKeyValue("event_type", "USER_AUTH_BLOCKED")
                    .addKeyValue("retry_after_seconds", retryAfter.toSeconds())
                    .log("Authentication blocked after repeated failures");
            throw new TooManyLoginAttemptsException(retryAfter);
        });

        Optional<User> found = userRepository.findByEmail(normalizedEmail);
        String passwordHash = found.map(User::passwordHash).orElse(unknownUserPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(rawPassword, passwordHash);

        if (found.isEmpty() || !passwordMatches) {
            loginAttempts.recordFailure(normalizedEmail, clientIp);
            log.atWarn()
                    .addKeyValue("event_type", "USER_AUTH_FAILED")
                    .addKeyValue("user_id", found.map(User::id).orElse(null))
                    .addKeyValue("reason", found.isEmpty() ? "USER_NOT_FOUND" : "INVALID_PASSWORD")
                    .log("Authentication failed");
            throw new InvalidCredentialsException("Credenciais inválidas.");
        }

        User user = found.get();
        if (!user.isActive()) {
            log.atWarn()
                    .addKeyValue("event_type", "USER_AUTH_FAILED")
                    .addKeyValue("user_id", user.id())
                    .addKeyValue("reason", "USER_INACTIVE")
                    .log("Authentication blocked for inactive user");
            throw new InvalidCredentialsException("USER_INACTIVE", "Conta de usuário inativa ou bloqueada.");
        }

        loginAttempts.reset(normalizedEmail, clientIp);

        Instant now = Instant.now(clock);
        UUID sessionId = UUID.randomUUID();
        TokenProviderPort.IssueTokenResult issuedAccess = tokenProvider.issueAccessToken(user, sessionId);
        String rawRefreshToken = tokenProvider.generateRefreshToken();
        String refreshTokenHash = tokenProvider.hashToken(rawRefreshToken);

        refreshTokenRepository.save(
                RefreshToken.issue(user.id(), refreshTokenHash, now, now.plus(REFRESH_TOKEN_TTL), sessionId)
        );

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
