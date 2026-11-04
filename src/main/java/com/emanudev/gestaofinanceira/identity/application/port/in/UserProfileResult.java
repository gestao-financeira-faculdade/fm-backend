package com.emanudev.gestaofinanceira.identity.application.port.in;

import com.emanudev.gestaofinanceira.identity.domain.model.Role;
import com.emanudev.gestaofinanceira.identity.domain.model.User;

import java.util.UUID;

public record UserProfileResult(
        UUID id,
        String fullName,
        String email,
        Role role,
        boolean active
) {

    public static UserProfileResult from(User user) {
        return new UserProfileResult(
                user.id(),
                user.fullName(),
                user.email(),
                user.role(),
                user.active()
        );
    }
}
