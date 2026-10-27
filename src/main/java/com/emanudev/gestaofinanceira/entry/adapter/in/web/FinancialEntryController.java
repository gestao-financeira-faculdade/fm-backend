package com.emanudev.gestaofinanceira.entry.adapter.in.web;

import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.CancelFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.CreateFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.FinancialEntryResponse;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.SettleFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.SettlementResponse;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.mapper.FinancialEntryWebMapper;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryFilter;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryLineageNode;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntrySummaryView;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryUseCaseService;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.SettlementCommand;
import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FinancialEntryController implements FinancialEntryControllerOpenApi {

    private final FinancialEntryUseCaseService entryService;
    private final Clock clock;
    private final FinancialEntryWebMapper financialEntryWebMapper;

    @Autowired
    public FinancialEntryController(
            FinancialEntryUseCaseService entryService,
            Clock clock,
            FinancialEntryWebMapper financialEntryWebMapper
    ) {
        this.entryService = entryService;
        this.clock = clock;
        this.financialEntryWebMapper = financialEntryWebMapper;
    }

    public FinancialEntryController(FinancialEntryUseCaseService entryService, Clock clock) {
        this(entryService, clock, new FinancialEntryWebMapper());
    }

    @Override
    public ResponseEntity<FinancialEntryResponse> createEntry(
            CreateFinancialEntryRequest request,
            String idempotencyKeyHeader
    ) {
        FinancialEntry created = entryService.createEntry(
                financialEntryWebMapper.toCreateCommand(request, idempotencyKeyHeader)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financialEntryWebMapper.toResponse(created, entryService));
    }

    @Override
    public ResponseEntity<PagedResult<FinancialEntrySummaryView>> listEntries(
            EntryType entryType,
            EntryStatus status,
            UUID categoryId,
            UUID accountId,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            String search,
            int page,
            int size
    ) {
        FinancialEntryFilter filter = new FinancialEntryFilter(
                entryType, status, categoryId, accountId, dueDateFrom, dueDateTo, search
        );
        PagedResult<FinancialEntrySummaryView> result = entryService.findEntries(filter, PageQuery.of(page, size));
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<FinancialEntryResponse> getEntry(UUID id) {
        FinancialEntry entry = entryService.getEntryById(id);
        return ResponseEntity.ok(financialEntryWebMapper.toResponse(entry, entryService));
    }

    @Override
    public ResponseEntity<SettlementResponse> settleEntry(
            UUID id,
            SettleFinancialEntryRequest request
    ) {
        FinancialEntry current = entryService.getEntryById(id);
        SettlementCommand command = financialEntryWebMapper.toSettlementCommand(request, current, clock);
        Long expectedVersion = financialEntryWebMapper.extractExpectedVersion(request);
        SettlementOutcome outcome = entryService.settleEntry(id, command, expectedVersion);
        return ResponseEntity.ok(financialEntryWebMapper.toSettlementResponse(outcome, entryService));
    }

    @Override
    public ResponseEntity<FinancialEntryResponse> cancelEntry(
            UUID id,
            CancelFinancialEntryRequest request,
            String reason
    ) {
        String resolvedReason = financialEntryWebMapper.resolveCancellationReason(request, reason);
        FinancialEntry cancelled = entryService.cancelEntry(id, resolvedReason);
        return ResponseEntity.ok(financialEntryWebMapper.toResponse(cancelled, entryService));
    }

    @Override
    public ResponseEntity<FinancialEntryResponse> deleteEntryLogically(
            UUID id,
            CancelFinancialEntryRequest request,
            String reason
    ) {
        String resolvedReason = financialEntryWebMapper.resolveCancellationReason(request, reason);
        FinancialEntry cancelled = entryService.cancelEntry(id, resolvedReason);
        return ResponseEntity.ok(financialEntryWebMapper.toResponse(cancelled, entryService));
    }

    @Override
    public ResponseEntity<List<FinancialEntryLineageNode>> getEntrySplits(UUID id) {
        return ResponseEntity.ok(entryService.getEntryLineage(id));
    }
}
