package com.emanudev.gestaofinanceira.audit.adapter.out.persistence;

import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogFilter;
import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogItemResponse;
import com.emanudev.gestaofinanceira.audit.application.port.in.QueryAuditLogsUseCase;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
public class JooqAuditQueryAdapter implements QueryAuditLogsUseCase {

    private static final Table<Record> T_AUDIT_LOGS = DSL.table(DSL.name("audit_logs"));

    private static final Field<UUID> F_ID = DSL.field(DSL.name("audit_logs", "id"), UUID.class);
    private static final Field<String> F_ENTITY_TYPE = DSL.field(DSL.name("audit_logs", "entity_type"), String.class);
    private static final Field<UUID> F_ENTITY_ID = DSL.field(DSL.name("audit_logs", "entity_id"), UUID.class);
    private static final Field<String> F_ACTION = DSL.field(DSL.name("audit_logs", "action"), String.class);
    private static final Field<UUID> F_ACTOR_USER_ID = DSL.field(DSL.name("audit_logs", "actor_user_id"), UUID.class);
    private static final Field<String> F_ACTOR_NAME = DSL.field(DSL.name("audit_logs", "actor_name"), String.class);
    private static final Field<String> F_ACTOR_ROLE = DSL.field(DSL.name("audit_logs", "actor_role"), String.class);
    private static final Field<String> F_ACTOR_TYPE = DSL.field(DSL.name("audit_logs", "actor_type"), String.class);
    private static final Field<String> F_IP_ADDRESS = DSL.field(DSL.name("audit_logs", "ip_address"), String.class);
    private static final Field<String> F_CORRELATION_ID = DSL.field(DSL.name("audit_logs", "correlation_id"), String.class);
    private static final Field<Object> F_PREVIOUS_STATE = DSL.field(DSL.name("audit_logs", "previous_state"), Object.class);
    private static final Field<Object> F_NEW_STATE = DSL.field(DSL.name("audit_logs", "new_state"), Object.class);
    private static final Field<OffsetDateTime> F_OCCURRED_AT =
            DSL.field(DSL.name("audit_logs", "occurred_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqAuditQueryAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public PagedResult<AuditLogItemResponse> queryAuditLogs(AuditLogFilter filter, PageQuery pageQuery) {
        AuditLogFilter effectiveFilter = filter != null ? filter : AuditLogFilter.empty();
        PageQuery effectivePage = pageQuery != null ? pageQuery : PageQuery.of(0, 20);

        List<Condition> conditions = buildConditions(effectiveFilter);

        long totalElements = Objects.requireNonNullElse(
                dsl.selectCount()
                        .from(T_AUDIT_LOGS)
                        .where(conditions)
                        .fetchOne(0, Long.class),
                0L
        );

        if (totalElements == 0L) {
            return PagedResult.of(List.of(), 0L, effectivePage.page(), effectivePage.size());
        }

        List<AuditLogItemResponse> items = dsl.select(
                        F_ID,
                        F_ENTITY_TYPE,
                        F_ENTITY_ID,
                        F_ACTION,
                        F_ACTOR_USER_ID,
                        F_ACTOR_NAME,
                        F_ACTOR_ROLE,
                        F_ACTOR_TYPE,
                        F_IP_ADDRESS,
                        F_CORRELATION_ID,
                        F_PREVIOUS_STATE,
                        F_NEW_STATE,
                        F_OCCURRED_AT
                )
                .from(T_AUDIT_LOGS)
                .where(conditions)
                .orderBy(F_OCCURRED_AT.desc(), F_ID.desc())
                .limit(effectivePage.size())
                .offset(effectivePage.offset())
                .fetch(this::mapRecordToResponse);

        return PagedResult.of(items, totalElements, effectivePage.page(), effectivePage.size());
    }

    @Override
    public List<AuditLogItemResponse> getEntryAuditHistory(UUID entryId) {
        Objects.requireNonNull(entryId, "entryId must not be null");

        return dsl.select(
                        F_ID,
                        F_ENTITY_TYPE,
                        F_ENTITY_ID,
                        F_ACTION,
                        F_ACTOR_USER_ID,
                        F_ACTOR_NAME,
                        F_ACTOR_ROLE,
                        F_ACTOR_TYPE,
                        F_IP_ADDRESS,
                        F_CORRELATION_ID,
                        F_PREVIOUS_STATE,
                        F_NEW_STATE,
                        F_OCCURRED_AT
                )
                .from(T_AUDIT_LOGS)
                .where(F_ENTITY_ID.eq(entryId))
                .orderBy(F_OCCURRED_AT.desc(), F_ID.desc())
                .fetch(this::mapRecordToResponse);
    }

    private List<Condition> buildConditions(AuditLogFilter filter) {
        List<Condition> conditions = new ArrayList<>();

        if (filter.entityType() != null && !filter.entityType().isBlank()) {
            conditions.add(F_ENTITY_TYPE.eq(filter.entityType().trim()));
        }
        if (filter.entityId() != null) {
            conditions.add(F_ENTITY_ID.eq(filter.entityId()));
        }
        if (filter.actorUserId() != null) {
            conditions.add(F_ACTOR_USER_ID.eq(filter.actorUserId()));
        }
        if (filter.action() != null) {
            conditions.add(F_ACTION.eq(filter.action().name()));
        }
        if (filter.startDate() != null) {
            conditions.add(F_OCCURRED_AT.ge(OffsetDateTime.ofInstant(filter.startDate(), ZoneOffset.UTC)));
        }
        if (filter.endDate() != null) {
            conditions.add(F_OCCURRED_AT.le(OffsetDateTime.ofInstant(filter.endDate(), ZoneOffset.UTC)));
        }

        return conditions;
    }

    private AuditLogItemResponse mapRecordToResponse(Record record) {
        String actionStr = record.get(F_ACTION);
        AuditAction action = actionStr != null ? AuditAction.valueOf(actionStr) : null;
        OffsetDateTime occurredAtOdt = record.get(F_OCCURRED_AT);
        Instant occurredAt = occurredAtOdt != null ? occurredAtOdt.toInstant() : null;

        return new AuditLogItemResponse(
                record.get(F_ID),
                record.get(F_ENTITY_TYPE),
                record.get(F_ENTITY_ID),
                action,
                record.get(F_ACTOR_USER_ID),
                record.get(F_ACTOR_NAME),
                record.get(F_ACTOR_ROLE),
                record.get(F_ACTOR_TYPE),
                record.get(F_IP_ADDRESS),
                record.get(F_CORRELATION_ID),
                extractJsonString(record.get(F_PREVIOUS_STATE)),
                extractJsonString(record.get(F_NEW_STATE)),
                occurredAt
        );
    }

    private String extractJsonString(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof JSONB jsonb) {
            return jsonb.data();
        }
        if (rawValue instanceof JSON json) {
            return json.data();
        }
        return rawValue.toString();
    }
}
