package com.emanudev.gestaofinanceira.identity.application.port.out;

import com.emanudev.gestaofinanceira.identity.domain.model.User;

import java.time.Instant;
import java.util.UUID;

public interface TokenProviderPort {

    record IssueTokenResult(
            String accessToken,
            long expiresInSeconds,
            Instant expiresAt
    ) {
    }

    IssueTokenResult issueAccessToken(User user, UUID sessionId);

    String generateRefreshToken();

    String hashToken(String rawToken);
}
