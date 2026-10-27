package com.emanudev.gestaofinanceira.identity.application.port.in;

public record AuthenticateUserCommand(
        String email,
        String password
) {
}
