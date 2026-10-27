package com.emanudev.gestaofinanceira.audit.adapter.out.persistence;

import com.emanudev.gestaofinanceira.audit.adapter.in.aop.FinancialAuditAspect;
import com.emanudev.gestaofinanceira.audit.application.port.out.AuditLogRepositoryPort;
import com.emanudev.gestaofinanceira.audit.domain.model.AuditLogEntry;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class AuditPersistenceAdapter implements AuditTrailPort, AuditLogRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(AuditPersistenceAdapter.class);
    private static final String MDC_CORRELATION_ID = "correlation_id";
    private static final String MDC_TRACE_ID = "traceId";

    private final SpringDataAuditLogRepository repository;
    private final ActorContextPort actorContextPort;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuditPersistenceAdapter(
            SpringDataAuditLogRepository repository,
            ActorContextPort actorContextPort,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.actorContextPort = actorContextPort;
        this.clock = clock;
        this.objectMapper = objectMapper != null ? objectMapper : JsonMapper.builder().build();
    }

    public AuditPersistenceAdapter(
            SpringDataAuditLogRepository repository,
            ActorContextPort actorContextPort,
            Clock clock
    ) {
        this(repository, actorContextPort, clock, JsonMapper.builder().build());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordTransition(
            String entityType,
            UUID entityId,
            AuditAction action,
            Object previousState,
            Object newState
    ) {
        FinancialAuditAspect.markExplicitAuditRecorded();

        ActorContext actor = resolveCurrentActor();
        String correlationId = resolveCorrelationId();
        String previousJson = serializeState(previousState);
        String newJson = serializeState(newState);
        Instant occurredAt = Instant.now(clock);

        AuditLogEntry entry = AuditLogEntry.create(
                entityType,
                entityId,
                action,
                actor,
                correlationId,
                previousJson,
                newJson,
                occurredAt
        );

        AuditLogEntry saved = save(entry);

        log.atInfo()
                .addKeyValue("event_type", "AUDIT_TRAIL_RECORDED")
                .addKeyValue("audit_id", saved.id())
                .addKeyValue("entity_type", saved.entityType())
                .addKeyValue("entity_id", saved.entityId())
                .addKeyValue("audit_action", saved.action().name())
                .addKeyValue("actor_type", saved.actorType())
                .addKeyValue("actor_user_id", saved.actorUserId())
                .addKeyValue("correlation_id", saved.correlationId())
                .log("Immutable audit trail entry recorded");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLogEntry save(AuditLogEntry entry) {
        AuditLogJpaEntity entity = AuditLogJpaEntity.fromDomain(entry);
        AuditLogJpaEntity persisted = repository.save(entity);
        return persisted.toDomain();
    }

    private ActorContext resolveCurrentActor() {
        if (actorContextPort == null) {
            return ActorContext.system();
        }
        ActorContext actor = actorContextPort.getCurrentActor();
        return actor != null ? actor : ActorContext.system();
    }

    private String resolveCorrelationId() {
        String correlationId = MDC.get(MDC_CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        String traceId = MDC.get(MDC_TRACE_ID);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return MDC.get("trace_id");
    }

    private String serializeState(Object state) {
        if (state == null) {
            return null;
        }
        if (state instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                return trimmed;
            }
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize audit state snapshot to JSON", ex);
        }
    }
}
