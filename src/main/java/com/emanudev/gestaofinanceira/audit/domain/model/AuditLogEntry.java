package com.emanudev.gestaofinanceira.audit.domain.model;

import com.emanudev.gestaofinanceira.shared.application.AuditAction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditLogEntry(
        UUID id,
        String entityType,
        UUID entityId,
        AuditAction action,
        UUID actorUserId,
        String actorName,
        String actorRole,
        String actorType,
        String ipAddress,
        String correlationId,
        String previousState,
        String newState,
        Instant occurredAt
) {

    public AuditLogEntry {
        Objects.requireNonNull(id, "id must not be null");
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType must not be blank");
        }
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (actorName == null || actorName.isBlank()) {
            throw new IllegalArgumentException("actorName must not be blank");
        }
        if (actorRole == null || actorRole.isBlank()) {
            throw new IllegalArgumentException("actorRole must not be blank");
        }
        if (actorType == null || actorType.isBlank()) {
            throw new IllegalArgumentException("actorType must not be blank");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static AuditLogEntry create(
            String entityType,
            UUID entityId,
            AuditAction action,
            UUID actorUserId,
            String actorName,
            String actorRole,
            String actorType,
            String ipAddress,
            String correlationId,
            String previousState,
            String newState,
            Instant occurredAt
    ) {
        return new AuditLogEntry(
                UUID.randomUUID(),
                entityType,
                entityId,
                action,
                actorUserId,
                actorName != null && !actorName.isBlank() ? actorName : "SYSTEM",
                actorRole != null && !actorRole.isBlank() ? actorRole : "SYSTEM",
                actorType != null && !actorType.isBlank() ? actorType : "SYSTEM",
                ipAddress,
                correlationId,
                previousState,
                newState,
                occurredAt
        );
    }
}
