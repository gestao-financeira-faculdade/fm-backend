package com.emanudev.gestaofinanceira.audit.application.port.in;

import com.emanudev.gestaofinanceira.shared.application.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditLogFilter(
        String entityType,
        UUID entityId,
        UUID actorUserId,
        AuditAction action,
        Instant startDate,
        Instant endDate
) {

    public static AuditLogFilter empty() {
        return new AuditLogFilter(null, null, null, null, null, null);
    }
}
