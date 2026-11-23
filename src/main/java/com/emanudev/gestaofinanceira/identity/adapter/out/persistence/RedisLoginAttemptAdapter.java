package com.emanudev.gestaofinanceira.identity.adapter.out.persistence;

import com.emanudev.gestaofinanceira.identity.application.port.out.LoginAttemptPort;
import com.emanudev.gestaofinanceira.shared.config.ApplicationProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisLoginAttemptAdapter implements LoginAttemptPort {

    static final String ACCOUNT_KEY_PREFIX = "auth:login_failures:account:";
    static final String IP_KEY_PREFIX = "auth:login_failures:ip:";

    private final StringRedisTemplate redisTemplate;
    private final int maxFailuresPerAccount;
    private final int maxFailuresPerIp;
    private final Duration lockDuration;

    public RedisLoginAttemptAdapter(StringRedisTemplate redisTemplate, ApplicationProperties applicationProperties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.maxFailuresPerAccount = applicationProperties.getLoginMaxFailuresPerAccount();
        this.maxFailuresPerIp = applicationProperties.getLoginMaxFailuresPerIp();
        this.lockDuration = Duration.ofMinutes(applicationProperties.getLoginLockMinutes());
    }

    @Override
    public Optional<Duration> lockRemaining(String email, String clientIp) {
        for (Map.Entry<String, Integer> limit : limits(email, clientIp).entrySet()) {
            String count = redisTemplate.opsForValue().get(limit.getKey());
            if (count != null && Long.parseLong(count) >= limit.getValue()) {
                Long ttl = redisTemplate.getExpire(limit.getKey(), TimeUnit.SECONDS);
                return Optional.of(Duration.ofSeconds(ttl == null || ttl < 1 ? 1 : ttl));
            }
        }
        return Optional.empty();
    }

    @Override
    public void recordFailure(String email, String clientIp) {
        limits(email, clientIp).forEach((key, max) -> {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && (count == 1L || count >= max)) {
                redisTemplate.expire(key, lockDuration);
            }
        });
    }

    @Override
    public void reset(String email, String clientIp) {
        redisTemplate.delete(accountKey(email));
    }

    private Map<String, Integer> limits(String email, String clientIp) {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put(accountKey(email), maxFailuresPerAccount);
        if (clientIp != null && !clientIp.isBlank()) {
            limits.put(IP_KEY_PREFIX + clientIp.trim(), maxFailuresPerIp);
        }
        return limits;
    }

    private static String accountKey(String email) {
        return ACCOUNT_KEY_PREFIX + email.trim().toLowerCase(Locale.ROOT);
    }
}
