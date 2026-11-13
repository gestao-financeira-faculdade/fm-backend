package com.emanudev.gestaofinanceira.audit.adapter.in.web;

import com.emanudev.gestaofinanceira.audit.adapter.in.web.mapper.AuditLogWebMapper;
import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogFilter;
import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogItemResponse;
import com.emanudev.gestaofinanceira.audit.application.port.in.QueryAuditLogsUseCase;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class AuditLogController implements AuditLogControllerOpenApi {

    private final QueryAuditLogsUseCase queryAuditLogsUseCase;
    private final AuditLogWebMapper auditLogWebMapper;

    @Autowired
    public AuditLogController(
            QueryAuditLogsUseCase queryAuditLogsUseCase,
            AuditLogWebMapper auditLogWebMapper
    ) {
        this.queryAuditLogsUseCase = queryAuditLogsUseCase;
        this.auditLogWebMapper = auditLogWebMapper;
    }

    public AuditLogController(QueryAuditLogsUseCase queryAuditLogsUseCase) {
        this(queryAuditLogsUseCase, new AuditLogWebMapper());
    }

    @Override
    public ResponseEntity<PagedResult<AuditLogItemResponse>> listAuditLogs(
            String entityType,
            UUID entityId,
            UUID actorUserId,
            AuditAction action,
            Instant startDate,
            Instant endDate,
            int page,
            int size
    ) {
        AuditLogFilter filter = auditLogWebMapper.toFilter(
                entityType,
                entityId,
                actorUserId,
                action,
                startDate,
                endDate
        );
        PageQuery pageQuery = auditLogWebMapper.toPageQuery(page, size);
        PagedResult<AuditLogItemResponse> result = queryAuditLogsUseCase.queryAuditLogs(filter, pageQuery);
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<List<AuditLogItemResponse>> getEntryAuditHistory(UUID entryId) {
        List<AuditLogItemResponse> history = queryAuditLogsUseCase.getEntryAuditHistory(entryId);
        return ResponseEntity.ok(history);
    }
}
