package com.emanudev.gestaofinanceira.identity.application.port.in;

public record AuthenticationResult(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserProfileResult user
) {
}
