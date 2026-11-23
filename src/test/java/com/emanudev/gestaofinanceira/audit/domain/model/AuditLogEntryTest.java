package com.emanudev.gestaofinanceira.audit.domain.model;

import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogEntryTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-26T14:00:00Z");

    @Test
    void shouldCreateAuditLogEntryAndDefaultBlankActorFieldsToSystem() {
        UUID entityId = UUID.randomUUID();
        AuditLogEntry entry = AuditLogEntry.create(
                "FINANCIAL_ENTRY",
                entityId,
                AuditAction.AUTO_OVERDUE_TRANSITION,
                null,
                "   ",
                null,
                "",
                null,
                "corr-123",
                null,
                "{\"status\":\"OVERDUE\"}",
                FIXED_INSTANT
        );

        assertThat(entry.id()).isNotNull();
        assertThat(entry.entityType()).isEqualTo("FINANCIAL_ENTRY");
        assertThat(entry.entityId()).isEqualTo(entityId);
        assertThat(entry.actorName()).isEqualTo("SYSTEM");
        assertThat(entry.actorRole()).isEqualTo("SYSTEM");
        assertThat(entry.actorType()).isEqualTo("SYSTEM");
    }

    @Test
    void shouldRejectBlankEntityType() {
        assertThatThrownBy(() -> AuditLogEntry.create(
                "   ",
                UUID.randomUUID(),
                AuditAction.CREATE,
                UUID.randomUUID(),
                "Admin",
                "ADMIN",
                "USER",
                "127.0.0.1",
                "corr-1",
                null,
                "{}",
                FIXED_INSTANT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entityType must not be blank");
    }
}
