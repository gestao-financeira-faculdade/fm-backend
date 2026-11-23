package com.emanudev.gestaofinanceira.identity.application.port.out;

import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {

    enum ConsumeStatus { CONSUMED, ALREADY_REVOKED, NOT_FOUND }

    record Consumption(ConsumeStatus status, RefreshToken token) {

        public static Consumption notFound() {
            return new Consumption(ConsumeStatus.NOT_FOUND, null);
        }
    }

    RefreshToken save(@NotNull RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(@NotBlank String tokenHash);

    /**
     * Marks the token as revoked only if it is still active, in a single atomic step, so that concurrent
     * refreshes with the same token cannot both succeed.
     */
    Consumption consume(@NotBlank String tokenHash, @NotNull Instant now);

    void revokeFamily(@NotNull UUID familyId, @NotNull Instant revokedAt);

    void revokeAllByUserId(@NotNull UUID userId, @NotNull Instant revokedAt);
}
