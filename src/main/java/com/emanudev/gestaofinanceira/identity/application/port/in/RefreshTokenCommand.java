package com.emanudev.gestaofinanceira.identity.application.port.in;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenCommand(
        @NotBlank(message = "O refresh token é obrigatório")
        String refreshToken
) {
}
