package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialEntryQueryPort {

    PagedResult<FinancialEntrySummaryView> findEntries(FinancialEntryFilter filter, PageQuery pageQuery);

    Optional<FinancialEntrySummaryView> findEntrySummaryById(UUID entryId);

    List<FinancialEntryLineageNode> getEntryLineage(UUID entryId);
}
