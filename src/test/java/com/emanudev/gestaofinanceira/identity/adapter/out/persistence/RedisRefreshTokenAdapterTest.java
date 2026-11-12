package com.emanudev.gestaofinanceira.identity.adapter.out.persistence;

import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenAdapterTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-01T12:00:00Z");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private RedisRefreshTokenAdapter adapter;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        adapter = new RedisRefreshTokenAdapter(redisTemplate, clock);
    }

    @Test
    @DisplayName("Deve salvar refresh token no Redis com hash, índice por usuário e TTL calculado")
    @SuppressWarnings("unchecked")
    void shouldSaveRefreshTokenInRedisWithTtl() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(7));
        RefreshToken token = new RefreshToken(tokenId, userId, "sha256-hash", expiresAt, null, FIXED_NOW);

        RefreshToken saved = adapter.save(token);

        assertThat(saved).isEqualTo(token);
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq("auth:refresh_token:sha256-hash"), mapCaptor.capture());
        verify(setOperations).add("auth:user_refresh_tokens:" + userId, "sha256-hash");
        verify(redisTemplate).expire("auth:refresh_token:sha256-hash", Duration.ofDays(7));
        verify(redisTemplate).expire("auth:user_refresh_tokens:" + userId, Duration.ofDays(7));

        Map<String, String> storedMap = mapCaptor.getValue();
        assertThat(storedMap)
                .containsEntry("id", tokenId.toString())
                .containsEntry("userId", userId.toString())
                .containsEntry("tokenHash", "sha256-hash")
                .containsEntry("expiresAt", expiresAt.toString())
                .containsEntry("revokedAt", "");
    }

    @Test
    @DisplayName("Deve recuperar refresh token armazenado no Redis pelo hash")
    void shouldFindRefreshTokenByHashInRedis() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(7));

        when(hashOperations.entries("auth:refresh_token:sha256-hash")).thenReturn(Map.of(
                "id", tokenId.toString(),
                "userId", userId.toString(),
                "tokenHash", "sha256-hash",
                "expiresAt", expiresAt.toString(),
                "revokedAt", "",
                "createdAt", FIXED_NOW.toString()
        ));

        Optional<RefreshToken> found = adapter.findByTokenHash("sha256-hash");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(tokenId);
        assertThat(found.get().userId()).isEqualTo(userId);
        assertThat(found.get().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("Deve revogar todos os refresh tokens ativos de um usuário no Redis")
    void shouldRevokeAllActiveTokensByUserIdInRedis() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(7));

        when(setOperations.members("auth:user_refresh_tokens:" + userId)).thenReturn(Set.of("hash-1"));
        when(hashOperations.entries("auth:refresh_token:hash-1")).thenReturn(Map.of(
                "id", tokenId.toString(),
                "userId", userId.toString(),
                "tokenHash", "hash-1",
                "expiresAt", expiresAt.toString(),
                "revokedAt", "",
                "createdAt", FIXED_NOW.toString()
        ));

        adapter.revokeAllByUserId(userId, FIXED_NOW);

        verify(hashOperations).putAll(eq("auth:refresh_token:hash-1"), org.mockito.ArgumentMatchers.argThat(map ->
                FIXED_NOW.toString().equals(map.get("revokedAt"))
        ));
    }
}
