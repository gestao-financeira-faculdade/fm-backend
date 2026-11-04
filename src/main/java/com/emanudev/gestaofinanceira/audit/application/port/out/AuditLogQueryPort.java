package com.emanudev.gestaofinanceira.audit.application.port.out;

import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogFilter;
import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogItemResponse;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;

import java.util.List;
import java.util.UUID;

public interface AuditLogQueryPort {

    PagedResult<AuditLogItemResponse> queryAuditLogs(AuditLogFilter filter, PageQuery pageQuery);

    List<AuditLogItemResponse> getEntryAuditHistory(UUID entryId);
}
