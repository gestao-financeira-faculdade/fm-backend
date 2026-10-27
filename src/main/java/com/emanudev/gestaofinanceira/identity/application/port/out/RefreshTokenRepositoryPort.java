package com.emanudev.gestaofinanceira.identity.application.port.out;

import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void revokeAllByUserId(UUID userId, Instant revokedAt);
}
