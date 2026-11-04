package com.emanudev.gestaofinanceira.identity.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserSummaryResponse user
) {
    public static TokenResponse from(AuthenticationResult result) {
        return new TokenResponse(
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                UserSummaryResponse.from(result.user())
        );
    }
}
