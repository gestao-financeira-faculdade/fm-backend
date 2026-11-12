package com.emanudev.gestaofinanceira.audit.adapter.out.persistence;

import com.emanudev.gestaofinanceira.audit.domain.model.AuditLogEntry;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Immutable
public class AuditLogJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "entity_type", length = 50, nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 40, nullable = false, updatable = false)
    private AuditAction action;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_name", length = 150, nullable = false, updatable = false)
    private String actorName;

    @Column(name = "actor_role", length = 30, nullable = false, updatable = false)
    private String actorRole;

    @Column(name = "actor_type", length = 20, nullable = false, updatable = false)
    private String actorType;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_state", columnDefinition = "jsonb", updatable = false)
    private String previousState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_state", columnDefinition = "jsonb", updatable = false)
    private String newState;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditLogJpaEntity() {
    }

    public AuditLogJpaEntity(
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
        this.id = id;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.actorType = actorType;
        this.ipAddress = ipAddress;
        this.correlationId = correlationId;
        this.previousState = previousState;
        this.newState = newState;
        this.occurredAt = occurredAt;
    }

    public static AuditLogJpaEntity fromDomain(AuditLogEntry entry) {
        return new AuditLogJpaEntity(
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

    public AuditLogEntry toDomain() {
        return new AuditLogEntry(
                id,
                entityType,
                entityId,
                action,
                actorUserId,
                actorName,
                actorRole,
                actorType,
                ipAddress,
                correlationId,
                previousState,
                newState,
                occurredAt
        );
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    @Transient
    public boolean isNew() {
        return true;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getActorType() {
        return actorType;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getPreviousState() {
        return previousState;
    }

    public String getNewState() {
        return newState;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
