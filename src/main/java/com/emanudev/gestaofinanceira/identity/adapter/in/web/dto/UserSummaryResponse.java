package com.emanudev.gestaofinanceira.identity.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.identity.application.port.in.UserProfileResult;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String fullName,
        String email,
        String role,
        boolean active
) {
    public static UserSummaryResponse from(UserProfileResult profile) {
        return new UserSummaryResponse(
                profile.id(),
                profile.fullName(),
                profile.email(),
                profile.role().name(),
                profile.active()
        );
    }
}
