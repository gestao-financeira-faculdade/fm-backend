package com.emanudev.gestaofinanceira.shared.application;

import java.util.UUID;

public record ActorContext(
        UUID userId,
        String userName,
        String role,
        String ipAddress,
        String actorType
) {

    public static final String ACTOR_TYPE_SYSTEM = "SYSTEM";
    public static final String ACTOR_TYPE_USER = "USER";

    public static ActorContext system() {
        return new ActorContext(null, "SYSTEM_SCHEDULER", "SYSTEM", "127.0.0.1", ACTOR_TYPE_SYSTEM);
    }

    public static ActorContext user(UUID userId, String userName, String role, String ipAddress) {
        return new ActorContext(
                userId,
                userName != null ? userName : "UNKNOWN",
                role != null ? role : "UNKNOWN",
                ipAddress != null ? ipAddress : "0.0.0.0",
                ACTOR_TYPE_USER
        );
    }

    public boolean isSystem() {
        return ACTOR_TYPE_SYSTEM.equals(actorType);
    }
}
