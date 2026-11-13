package com.emanudev.gestaofinanceira.audit.application.usecase;

import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogFilter;
import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogItemResponse;
import com.emanudev.gestaofinanceira.audit.application.port.in.QueryAuditLogsUseCase;
import com.emanudev.gestaofinanceira.audit.application.port.out.AuditLogQueryPort;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuditLogQueryUseCase implements QueryAuditLogsUseCase {

    private final AuditLogQueryPort auditLogQueryPort;

    public AuditLogQueryUseCase(AuditLogQueryPort auditLogQueryPort) {
        this.auditLogQueryPort = Objects.requireNonNull(auditLogQueryPort, "auditLogQueryPort must not be null");
    }

    @Override
    public PagedResult<AuditLogItemResponse> queryAuditLogs(AuditLogFilter filter, PageQuery pageQuery) {
        AuditLogFilter effectiveFilter = filter != null ? filter : AuditLogFilter.empty();
        PageQuery effectivePage = pageQuery != null ? pageQuery : PageQuery.of(0, 20);
        return auditLogQueryPort.queryAuditLogs(effectiveFilter, effectivePage);
    }

    @Override
    public List<AuditLogItemResponse> getEntryAuditHistory(UUID entryId) {
        Objects.requireNonNull(entryId, "entryId must not be null");
        return auditLogQueryPort.getEntryAuditHistory(entryId);
    }
}
