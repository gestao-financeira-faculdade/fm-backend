package com.emanudev.gestaofinanceira.identity.application.port.out;

import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {

    RefreshToken save(@NotNull RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(@NotBlank String tokenHash);

    void revokeAllByUserId(@NotNull UUID userId, @NotNull Instant revokedAt);
}
