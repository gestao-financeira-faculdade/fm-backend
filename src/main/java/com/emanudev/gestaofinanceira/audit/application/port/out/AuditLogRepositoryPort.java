package com.emanudev.gestaofinanceira.audit.application.port.out;

import com.emanudev.gestaofinanceira.audit.domain.model.AuditLogEntry;

public interface AuditLogRepositoryPort {

    AuditLogEntry save(AuditLogEntry entry);
}
