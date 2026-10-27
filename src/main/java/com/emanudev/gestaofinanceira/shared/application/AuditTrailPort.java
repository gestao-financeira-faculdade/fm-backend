package com.emanudev.gestaofinanceira.shared.application;

import java.util.UUID;

public interface AuditTrailPort {

    void recordTransition(
            String entityType,
            UUID entityId,
            AuditAction action,
            Object previousState,
            Object newState
    );
}
