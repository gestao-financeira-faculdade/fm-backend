package com.emanudev.gestaofinanceira.identity.adapter.out.persistence;

import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class RedisRefreshTokenAdapter implements RefreshTokenRepositoryPort {

    static final String TOKEN_KEY_PREFIX = "auth:refresh_token:";
    static final String USER_INDEX_KEY_PREFIX = "auth:user_refresh_tokens:";

    private static final String FIELD_ID = "id";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_TOKEN_HASH = "tokenHash";
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    private static final String FIELD_REVOKED_AT = "revokedAt";
    private static final String FIELD_CREATED_AT = "createdAt";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RedisRefreshTokenAdapter(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");

        String tokenKey = buildTokenKey(refreshToken.tokenHash());
        String userIndexKey = buildUserIndexKey(refreshToken.userId());

        redisTemplate.opsForHash().putAll(tokenKey, toRedisMap(refreshToken));
        redisTemplate.opsForSet().add(userIndexKey, refreshToken.tokenHash());

        Duration ttl = computeTtl(refreshToken.expiresAt());
        if (!ttl.isZero() && !ttl.isNegative()) {
            redisTemplate.expire(tokenKey, ttl);
            redisTemplate.expire(userIndexKey, ttl);
        }

        return refreshToken;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        String tokenKey = buildTokenKey(tokenHash.trim());
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(tokenKey);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromRedisMap(entries));
    }

    @Override
    public void revokeAllByUserId(UUID userId, Instant revokedAt) {
        String userIndexKey = buildUserIndexKey(userId);
        Set<String> tokenHashes = redisTemplate.opsForSet().members(userIndexKey);
        if (tokenHashes == null || tokenHashes.isEmpty()) {
            return;
        }

        for (String tokenHash : tokenHashes) {
            findByTokenHash(tokenHash)
                    .filter(token -> !token.isRevoked())
                    .ifPresent(token -> save(token.revoke(revokedAt)));
        }
    }

    private Duration computeTtl(Instant expiresAt) {
        Instant now = Instant.now(clock);
        Duration remaining = Duration.between(now, expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private static String buildTokenKey(String tokenHash) {
        return TOKEN_KEY_PREFIX + tokenHash;
    }

    private static String buildUserIndexKey(UUID userId) {
        return USER_INDEX_KEY_PREFIX + userId;
    }

    private static Map<String, String> toRedisMap(RefreshToken token) {
        return Map.of(
                FIELD_ID, token.id().toString(),
                FIELD_USER_ID, token.userId().toString(),
                FIELD_TOKEN_HASH, token.tokenHash(),
                FIELD_EXPIRES_AT, token.expiresAt().toString(),
                FIELD_REVOKED_AT, token.revokedAt() != null ? token.revokedAt().toString() : "",
                FIELD_CREATED_AT, token.createdAt().toString()
        );
    }

    private static RefreshToken fromRedisMap(Map<Object, Object> entries) {
        String revokedAtRaw = stringValue(entries.get(FIELD_REVOKED_AT));
        Instant revokedAt = (revokedAtRaw == null || revokedAtRaw.isBlank()) ? null : Instant.parse(revokedAtRaw);

        return new RefreshToken(
                UUID.fromString(stringValue(entries.get(FIELD_ID))),
                UUID.fromString(stringValue(entries.get(FIELD_USER_ID))),
                stringValue(entries.get(FIELD_TOKEN_HASH)),
                Instant.parse(stringValue(entries.get(FIELD_EXPIRES_AT))),
                revokedAt,
                Instant.parse(stringValue(entries.get(FIELD_CREATED_AT)))
        );
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
