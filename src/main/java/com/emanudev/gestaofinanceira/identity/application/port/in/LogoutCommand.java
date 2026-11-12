package com.emanudev.gestaofinanceira.identity.application.port.in;

import java.util.UUID;

public record LogoutCommand(
        String refreshToken,
        UUID userId
) {
}
