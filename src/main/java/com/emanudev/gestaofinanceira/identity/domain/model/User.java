package com.emanudev.gestaofinanceira.identity.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record User(
        UUID id,
        String fullName,
        String email,
        String passwordHash,
        Role role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public User {
        Objects.requireNonNull(id, "User id must not be null");
        Objects.requireNonNull(fullName, "User fullName must not be null");
        Objects.requireNonNull(email, "User email must not be null");
        Objects.requireNonNull(passwordHash, "User passwordHash must not be null");
        Objects.requireNonNull(role, "User role must not be null");
        Objects.requireNonNull(createdAt, "User createdAt must not be null");
        Objects.requireNonNull(updatedAt, "User updatedAt must not be null");

        if (fullName.isBlank()) {
            throw new IllegalArgumentException("User fullName must not be blank");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("User email must not be blank");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("User passwordHash must not be blank");
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        fullName = fullName.trim();
    }

    public boolean isActive() {
        return active;
    }

    public User deactivate(Instant now) {
        Objects.requireNonNull(now, "Timestamp must not be null");
        return new User(id, fullName, email, passwordHash, role, false, createdAt, now);
    }

    public User activate(Instant now) {
        Objects.requireNonNull(now, "Timestamp must not be null");
        return new User(id, fullName, email, passwordHash, role, true, createdAt, now);
    }
}
