package com.emanudev.gestaofinanceira.identity.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record LogoutCommand(
        String refreshToken,
        UUID userId,
        UUID sessionId,
        String accessTokenId,
        Instant accessTokenExpiresAt
) {

    public LogoutCommand(String refreshToken, UUID userId) {
        this(refreshToken, userId, null, null, null);
    }
}
