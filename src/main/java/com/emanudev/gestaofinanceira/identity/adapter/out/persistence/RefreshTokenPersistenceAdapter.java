package com.emanudev.gestaofinanceira.identity.adapter.out.persistence;

import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepositoryPort {

    private final SpringDataRefreshTokenRepository repository;

    public RefreshTokenPersistenceAdapter(SpringDataRefreshTokenRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        RefreshTokenJpaEntity saved = repository.save(RefreshTokenJpaEntity.fromDomain(refreshToken));
        return saved.toDomain();
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(tokenHash)
                .map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    public void revokeAllByUserId(UUID userId, Instant revokedAt) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        repository.revokeAllActiveByUserId(userId, revokedAt);
    }
}
