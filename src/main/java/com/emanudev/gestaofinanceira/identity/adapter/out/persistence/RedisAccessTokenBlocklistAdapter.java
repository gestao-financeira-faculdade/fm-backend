package com.emanudev.gestaofinanceira.identity.adapter.out.persistence;

import com.emanudev.gestaofinanceira.identity.application.port.out.AccessTokenBlocklistPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisAccessTokenBlocklistAdapter implements AccessTokenBlocklistPort {

    static final String KEY_PREFIX = "auth:revoked_access_token:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RedisAccessTokenBlocklistAdapter(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void block(String tokenId, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(clock), expiresAt);
        if (tokenId == null || remaining.isNegative() || remaining.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + tokenId, "1", remaining);
    }

    @Override
    public boolean isBlocked(String tokenId) {
        return tokenId != null && Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + tokenId));
    }
}
