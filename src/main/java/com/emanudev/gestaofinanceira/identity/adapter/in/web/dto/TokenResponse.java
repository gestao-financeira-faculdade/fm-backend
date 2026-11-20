package com.emanudev.gestaofinanceira.identity.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.identity.application.port.in.AuthenticationResult;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        UserSummaryResponse user
) {

    public static final String BEARER = "Bearer";

    public static TokenResponse from(AuthenticationResult result) {
        return new TokenResponse(
                result.accessToken(),
                BEARER,
                result.expiresIn(),
                result.refreshToken(),
                UserSummaryResponse.from(result.user())
        );
    }
}
