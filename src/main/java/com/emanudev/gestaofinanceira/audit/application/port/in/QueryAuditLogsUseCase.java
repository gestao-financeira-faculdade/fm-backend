package com.emanudev.gestaofinanceira.audit.application.port.in;

import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;

import java.util.List;
import java.util.UUID;

public interface QueryAuditLogsUseCase {

    PagedResult<AuditLogItemResponse> queryAuditLogs(AuditLogFilter filter, PageQuery pageQuery);

    List<AuditLogItemResponse> getEntryAuditHistory(UUID entryId);
}
