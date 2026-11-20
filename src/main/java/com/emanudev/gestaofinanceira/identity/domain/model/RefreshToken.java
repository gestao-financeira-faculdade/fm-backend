package com.emanudev.gestaofinanceira.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        UUID familyId
) {

    public RefreshToken {
        Objects.requireNonNull(id, "RefreshToken id must not be null");
        Objects.requireNonNull(userId, "RefreshToken userId must not be null");
        Objects.requireNonNull(tokenHash, "RefreshToken tokenHash must not be null");
        Objects.requireNonNull(expiresAt, "RefreshToken expiresAt must not be null");
        Objects.requireNonNull(createdAt, "RefreshToken createdAt must not be null");
        Objects.requireNonNull(familyId, "RefreshToken familyId must not be null");

        if (tokenHash.isBlank()) {
            throw new IllegalArgumentException("RefreshToken tokenHash must not be blank");
        }
    }

    public RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant revokedAt, Instant createdAt) {
        this(id, userId, tokenHash, expiresAt, revokedAt, createdAt, id);
    }

    public static RefreshToken issue(UUID userId, String tokenHash, Instant now, Instant expiresAt, UUID familyId) {
        return new RefreshToken(UUID.randomUUID(), userId, tokenHash, expiresAt, null, now, familyId);
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "Now instant must not be null");
        return !now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isValid(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    public RefreshToken revoke(Instant now) {
        Objects.requireNonNull(now, "Revocation timestamp must not be null");
        if (this.revokedAt != null) {
            return this;
        }
        return new RefreshToken(id, userId, tokenHash, expiresAt, now, createdAt, familyId);
    }
}
