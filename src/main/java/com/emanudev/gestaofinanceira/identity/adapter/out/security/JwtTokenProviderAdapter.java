package com.emanudev.gestaofinanceira.identity.adapter.out.security;

import com.emanudev.gestaofinanceira.identity.application.port.out.TokenProviderPort;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class JwtTokenProviderAdapter implements TokenProviderPort {

    public static final long DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 900L;

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final long accessTokenTtlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenProviderAdapter(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${app.security.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds
    ) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.accessTokenTtlSeconds = accessTokenTtlSeconds > 0 ? accessTokenTtlSeconds : DEFAULT_ACCESS_TOKEN_TTL_SECONDS;
    }

    @Override
    public IssueTokenResult issueAccessToken(User user) {
        Objects.requireNonNull(user, "user must not be null");

        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plusSeconds(accessTokenTtlSeconds);

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("gestao-financeira")
                .subject(user.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", user.email())
                .claim("name", user.fullName())
                .claim("role", user.role().name())
                .build();

        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
        return new IssueTokenResult(tokenValue, accessTokenTtlSeconds, expiresAt);
    }

    @Override
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Override
    public String hashToken(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in JVM", e);
        }
    }
}
