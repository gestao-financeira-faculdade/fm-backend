package com.emanudev.gestaofinanceira.identity.adapter.out.persistence;

import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort.ConsumeStatus;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import com.emanudev.gestaofinanceira.shared.config.ApplicationProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("isDockerAvailable")
class RedisIdentityAdaptersContainerTest {

    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private final Clock clock = Clock.systemUTC();

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @BeforeEach
    void flush() {
        redisTemplate.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
    }

    @Test
    @DisplayName("Refresh paralelo com o mesmo token: só uma requisição consome, as outras veem o token já revogado")
    void shouldConsumeRefreshTokenOnlyOnceUnderConcurrency() throws Exception {
        RedisRefreshTokenAdapter adapter = new RedisRefreshTokenAdapter(redisTemplate, clock);
        Instant now = Instant.now(clock);
        adapter.save(RefreshToken.issue(UUID.randomUUID(), "hash-concorrente", now, now.plus(Duration.ofDays(7)), UUID.randomUUID()));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ConsumeStatus>> attempts = IntStream.range(0, 8)
                    .<Callable<ConsumeStatus>>mapToObj(i -> () -> adapter.consume("hash-concorrente", Instant.now(clock)).status())
                    .toList();
            List<ConsumeStatus> results = pool.invokeAll(attempts).stream().map(RedisIdentityAdaptersContainerTest::join).toList();

            assertThat(results).filteredOn(ConsumeStatus.CONSUMED::equals).hasSize(1);
            assertThat(results).filteredOn(ConsumeStatus.ALREADY_REVOKED::equals).hasSize(7);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Revogar a sessão revoga todos os refresh tokens dela e preserva as outras sessões do usuário")
    void shouldRevokeOnlyTokensOfTheGivenSession() {
        RedisRefreshTokenAdapter adapter = new RedisRefreshTokenAdapter(redisTemplate, clock);
        Instant now = Instant.now(clock);
        UUID userId = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        UUID otherSession = UUID.randomUUID();
        adapter.save(RefreshToken.issue(userId, "s1-a", now, now.plus(Duration.ofDays(7)), session));
        adapter.save(RefreshToken.issue(userId, "s1-b", now, now.plus(Duration.ofDays(7)), session));
        adapter.save(RefreshToken.issue(userId, "s2-a", now, now.plus(Duration.ofDays(7)), otherSession));

        adapter.revokeFamily(session, now);

        assertThat(adapter.findByTokenHash("s1-a")).get().matches(RefreshToken::isRevoked);
        assertThat(adapter.findByTokenHash("s1-b")).get().matches(RefreshToken::isRevoked);
        assertThat(adapter.findByTokenHash("s2-a")).get().matches(token -> !token.isRevoked());
        assertThat(adapter.consume("s1-a", now).status()).isEqualTo(ConsumeStatus.ALREADY_REVOKED);
        assertThat(adapter.consume("inexistente", now).status()).isEqualTo(ConsumeStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Access token bloqueado no logout é reconhecido e expira junto com o token")
    void shouldBlockAccessTokenUntilItExpires() {
        RedisAccessTokenBlocklistAdapter blocklist = new RedisAccessTokenBlocklistAdapter(redisTemplate, clock);

        blocklist.block("jti-bloqueado", Instant.now(clock).plusSeconds(300));
        blocklist.block("jti-expirado", Instant.now(clock).minusSeconds(1));

        assertThat(blocklist.isBlocked("jti-bloqueado")).isTrue();
        assertThat(blocklist.isBlocked("jti-expirado")).isFalse();
        assertThat(blocklist.isBlocked("jti-livre")).isFalse();
        assertThat(redisTemplate.getExpire(RedisAccessTokenBlocklistAdapter.KEY_PREFIX + "jti-bloqueado")).isBetween(1L, 300L);
    }

    @Test
    @DisplayName("Cinco falhas bloqueiam a conta por 15 minutos; sucesso zera o contador da conta")
    void shouldLockAccountAfterConfiguredFailures() {
        RedisLoginAttemptAdapter attempts = new RedisLoginAttemptAdapter(redisTemplate, loginProperties(5, 20, 15));

        IntStream.range(0, 4).forEach(i -> attempts.recordFailure("Admin@Empresa.com", "10.0.0.1"));
        assertThat(attempts.lockRemaining("admin@empresa.com", "10.0.0.1")).isEmpty();

        attempts.recordFailure("admin@empresa.com", "10.0.0.2");
        assertThat(attempts.lockRemaining("admin@empresa.com", "10.0.0.9"))
                .hasValueSatisfying(remaining -> assertThat(remaining).isBetween(Duration.ofMinutes(14), Duration.ofMinutes(15)));

        attempts.reset("admin@empresa.com", "10.0.0.9");
        assertThat(attempts.lockRemaining("admin@empresa.com", "10.0.0.9")).isEmpty();
    }

    @Test
    @DisplayName("Falhas espalhadas por várias contas a partir do mesmo IP bloqueiam o IP")
    void shouldLockIpAfterFailuresAcrossAccounts() {
        RedisLoginAttemptAdapter attempts = new RedisLoginAttemptAdapter(redisTemplate, loginProperties(5, 3, 15));

        attempts.recordFailure("a@empresa.com", "198.51.100.4");
        attempts.recordFailure("b@empresa.com", "198.51.100.4");
        attempts.recordFailure("c@empresa.com", "198.51.100.4");

        assertThat(attempts.lockRemaining("d@empresa.com", "198.51.100.4")).isPresent();
        assertThat(attempts.lockRemaining("d@empresa.com", "198.51.100.5")).isEmpty();
    }

    private static ApplicationProperties loginProperties(int perAccount, int perIp, long lockMinutes) {
        ApplicationProperties properties = new ApplicationProperties();
        ReflectionTestUtils.setField(properties, "loginMaxFailuresPerAccount", perAccount);
        ReflectionTestUtils.setField(properties, "loginMaxFailuresPerIp", perIp);
        ReflectionTestUtils.setField(properties, "loginLockMinutes", lockMinutes);
        return properties;
    }

    private static <T> T join(Future<T> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
