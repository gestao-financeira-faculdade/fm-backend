package com.emanudev.gestaofinanceira.audit.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataAuditLogRepository extends JpaRepository<AuditLogJpaEntity, UUID> {
}
