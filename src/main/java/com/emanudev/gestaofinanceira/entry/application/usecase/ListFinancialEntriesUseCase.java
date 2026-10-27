package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryFilter;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryQueryPort;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntrySummaryView;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListFinancialEntriesUseCase {

    private final FinancialEntryQueryPort entryQueryPort;

    public ListFinancialEntriesUseCase(FinancialEntryQueryPort entryQueryPort) {
        this.entryQueryPort = entryQueryPort;
    }

    public PagedResult<FinancialEntrySummaryView> execute(FinancialEntryFilter filter, PageQuery pageQuery) {
        FinancialEntryFilter effectiveFilter = filter != null ? filter : FinancialEntryFilter.empty();
        PageQuery effectivePageQuery = pageQuery != null ? pageQuery : PageQuery.of(0, 20);
        return entryQueryPort.findEntries(effectiveFilter, effectivePageQuery);
    }
}
