package com.emanudev.gestaofinanceira.identity.adapter.out.persistence;

import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class RedisRefreshTokenAdapter implements RefreshTokenRepositoryPort {

    static final String TOKEN_KEY_PREFIX = "auth:refresh_token:";
    static final String USER_INDEX_KEY_PREFIX = "auth:user_refresh_tokens:";
    static final String FAMILY_INDEX_KEY_PREFIX = "auth:refresh_token_family:";

    private static final String FIELD_ID = "id";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_TOKEN_HASH = "tokenHash";
    private static final String FIELD_EXPIRES_AT = "expiresAt";
    private static final String FIELD_REVOKED_AT = "revokedAt";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_FAMILY_ID = "familyId";

    private static final RedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local revokedAt = redis.call('HGET', KEYS[1], 'revokedAt')
            if not revokedAt then return 0 end
            if revokedAt ~= '' then return 2 end
            redis.call('HSET', KEYS[1], 'revokedAt', ARGV[1])
            return 1
            """, Long.class);

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
        String familyIndexKey = buildFamilyIndexKey(refreshToken.familyId());

        redisTemplate.opsForHash().putAll(tokenKey, toRedisMap(refreshToken));
        redisTemplate.opsForSet().add(userIndexKey, refreshToken.tokenHash());
        redisTemplate.opsForSet().add(familyIndexKey, refreshToken.tokenHash());

        Duration ttl = computeTtl(refreshToken.expiresAt());
        if (!ttl.isZero() && !ttl.isNegative()) {
            redisTemplate.expire(tokenKey, ttl);
            redisTemplate.expire(userIndexKey, ttl);
            redisTemplate.expire(familyIndexKey, ttl);
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
    public Consumption consume(String tokenHash, Instant now) {
        String tokenKey = buildTokenKey(tokenHash.trim());
        Long outcome = redisTemplate.execute(CONSUME_SCRIPT, List.of(tokenKey), now.toString());
        if (outcome == null || outcome == 0L) {
            return Consumption.notFound();
        }
        ConsumeStatus status = outcome == 1L ? ConsumeStatus.CONSUMED : ConsumeStatus.ALREADY_REVOKED;
        return findByTokenHash(tokenHash)
                .map(token -> new Consumption(status, token))
                .orElseGet(Consumption::notFound);
    }

    @Override
    public void revokeFamily(UUID familyId, Instant revokedAt) {
        revokeAll(buildFamilyIndexKey(familyId), revokedAt);
    }

    @Override
    public void revokeAllByUserId(UUID userId, Instant revokedAt) {
        revokeAll(buildUserIndexKey(userId), revokedAt);
    }

    private void revokeAll(String indexKey, Instant revokedAt) {
        Set<String> tokenHashes = redisTemplate.opsForSet().members(indexKey);
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

    private static String buildFamilyIndexKey(UUID familyId) {
        return FAMILY_INDEX_KEY_PREFIX + familyId;
    }

    private static Map<String, String> toRedisMap(RefreshToken token) {
        return Map.of(
                FIELD_ID, token.id().toString(),
                FIELD_USER_ID, token.userId().toString(),
                FIELD_TOKEN_HASH, token.tokenHash(),
                FIELD_EXPIRES_AT, token.expiresAt().toString(),
                FIELD_REVOKED_AT, token.revokedAt() != null ? token.revokedAt().toString() : "",
                FIELD_CREATED_AT, token.createdAt().toString(),
                FIELD_FAMILY_ID, token.familyId().toString()
        );
    }

    private static RefreshToken fromRedisMap(Map<Object, Object> entries) {
        String revokedAtRaw = stringValue(entries.get(FIELD_REVOKED_AT));
        Instant revokedAt = (revokedAtRaw == null || revokedAtRaw.isBlank()) ? null : Instant.parse(revokedAtRaw);
        UUID id = UUID.fromString(stringValue(entries.get(FIELD_ID)));
        String familyIdRaw = stringValue(entries.get(FIELD_FAMILY_ID));

        return new RefreshToken(
                id,
                UUID.fromString(stringValue(entries.get(FIELD_USER_ID))),
                stringValue(entries.get(FIELD_TOKEN_HASH)),
                Instant.parse(stringValue(entries.get(FIELD_EXPIRES_AT))),
                revokedAt,
                Instant.parse(stringValue(entries.get(FIELD_CREATED_AT))),
                familyIdRaw == null || familyIdRaw.isBlank() ? id : UUID.fromString(familyIdRaw)
        );
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
