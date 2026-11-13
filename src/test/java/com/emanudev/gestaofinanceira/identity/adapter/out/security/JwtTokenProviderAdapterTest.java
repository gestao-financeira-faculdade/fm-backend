package com.emanudev.gestaofinanceira.identity.adapter.out.security;

import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort.IssueTokenResult;
import com.emanudev.gestaofinanceira.identity.domain.model.Role;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderAdapterTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-26T12:00:00Z");
    private static final long TTL_SECONDS = 900L;

    @Mock
    private JwtEncoder jwtEncoder;

    private JwtTokenProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JwtTokenProviderAdapter(jwtEncoder, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), TTL_SECONDS);
    }

    @Test
    void shouldIssueAccessTokenWithIdentityAndRoleClaims() {
        User user = new User(
                UUID.randomUUID(), "Maria Analista", "Maria@Empresa.com", "$2a$hash",
                Role.ANALISTA, true, FIXED_INSTANT, FIXED_INSTANT
        );
        Jwt jwt = Jwt.withTokenValue("signed.jwt.token")
                .header("alg", "HS256")
                .claim("sub", user.id().toString())
                .issuedAt(FIXED_INSTANT)
                .expiresAt(FIXED_INSTANT.plusSeconds(TTL_SECONDS))
                .build();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

        IssueTokenResult result = adapter.issueAccessToken(user);

        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());
        JwtClaimsSet claims = captor.getValue().getClaims();
        assertThat(claims.getSubject()).isEqualTo(user.id().toString());
        assertThat(claims.<String>getClaim("email")).isEqualTo("maria@empresa.com");
        assertThat(claims.<String>getClaim("role")).isEqualTo("ANALISTA");
        assertThat(claims.getExpiresAt()).isEqualTo(FIXED_INSTANT.plusSeconds(TTL_SECONDS));
        assertThat(result.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(result.expiresInSeconds()).isEqualTo(TTL_SECONDS);
    }

    @Test
    void shouldGenerateUniqueUrlSafeRefreshTokens() {
        String first = adapter.generateRefreshToken();
        String second = adapter.generateRefreshToken();

        assertThat(first).isNotEqualTo(second).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void shouldHashTokenDeterministicallyWithSha256Hex() {
        String hash = adapter.hashToken("refresh-token-value");

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        assertThat(adapter.hashToken("refresh-token-value")).isEqualTo(hash);
        assertThat(adapter.hashToken("other-value")).isNotEqualTo(hash);
    }
}
