package com.emanudev.gestaofinanceira.audit.adapter.in.web.mapper;

import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogFilter;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuditLogWebMapper {

    public AuditLogFilter toFilter(
            String entityType,
            UUID entityId,
            UUID actorUserId,
            AuditAction action,
            Instant startDate,
            Instant endDate
    ) {
        return new AuditLogFilter(
                entityType,
                entityId,
                actorUserId,
                action,
                startDate,
                endDate
        );
    }

    public PageQuery toPageQuery(int page, int size) {
        return PageQuery.of(page, size);
    }
}
