package com.emanudev.gestaofinanceira.audit.application.port.in;

import com.emanudev.gestaofinanceira.audit.domain.model.AuditLogEntry;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditLogItemResponse(
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

    public static AuditLogItemResponse fromDomain(AuditLogEntry entry) {
        return new AuditLogItemResponse(
                entry.id(),
                entry.entityType(),
                entry.entityId(),
                entry.action(),
                entry.actorUserId(),
                entry.actorName(),
                entry.actorRole(),
                entry.actorType(),
                entry.ipAddress(),
                entry.correlationId(),
                entry.previousState(),
                entry.newState(),
                entry.occurredAt()
        );
    }
}
