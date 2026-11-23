package com.emanudev.gestaofinanceira.entry.adapter.in.web;

import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.CreateFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.FinancialEntryResponse;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.SettleFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.SettlementResponse;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.mapper.FinancialEntryWebMapper;
import com.emanudev.gestaofinanceira.entry.application.CreateEntryResult;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryFilter;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryLineageNode;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntrySummaryView;
import com.emanudev.gestaofinanceira.entry.application.usecase.CancelFinancialEntryUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.CreateFinancialEntryUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.GetFinancialEntryLineageUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.GetFinancialEntryUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.ListFinancialEntriesUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.SettleFinancialEntryUseCase;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.SettlementCommand;
import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;
import com.emanudev.gestaofinanceira.shared.adapter.in.web.EntityTags;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class FinancialEntryController implements FinancialEntryControllerOpenApi {

    private final CreateFinancialEntryUseCase createFinancialEntryUseCase;
    private final ListFinancialEntriesUseCase listFinancialEntriesUseCase;
    private final GetFinancialEntryUseCase getFinancialEntryUseCase;
    private final SettleFinancialEntryUseCase settleFinancialEntryUseCase;
    private final CancelFinancialEntryUseCase cancelFinancialEntryUseCase;
    private final GetFinancialEntryLineageUseCase getFinancialEntryLineageUseCase;
    private final Clock clock;
    private final FinancialEntryWebMapper financialEntryWebMapper;

    public FinancialEntryController(
            CreateFinancialEntryUseCase createFinancialEntryUseCase,
            ListFinancialEntriesUseCase listFinancialEntriesUseCase,
            GetFinancialEntryUseCase getFinancialEntryUseCase,
            SettleFinancialEntryUseCase settleFinancialEntryUseCase,
            CancelFinancialEntryUseCase cancelFinancialEntryUseCase,
            GetFinancialEntryLineageUseCase getFinancialEntryLineageUseCase,
            Clock clock,
            FinancialEntryWebMapper financialEntryWebMapper
    ) {
        this.createFinancialEntryUseCase = createFinancialEntryUseCase;
        this.listFinancialEntriesUseCase = listFinancialEntriesUseCase;
        this.getFinancialEntryUseCase = getFinancialEntryUseCase;
        this.settleFinancialEntryUseCase = settleFinancialEntryUseCase;
        this.cancelFinancialEntryUseCase = cancelFinancialEntryUseCase;
        this.getFinancialEntryLineageUseCase = getFinancialEntryLineageUseCase;
        this.clock = clock;
        this.financialEntryWebMapper = financialEntryWebMapper;
    }

    @Override
    public ResponseEntity<FinancialEntryResponse> createEntry(
            CreateFinancialEntryRequest request,
            String idempotencyKeyHeader
    ) {
        CreateEntryResult result = createFinancialEntryUseCase.execute(
                financialEntryWebMapper.toCreateCommand(request, idempotencyKeyHeader)
        );
        FinancialEntry entry = result.entry();
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(entry.id())
                .toUri();
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .location(location)
                .eTag(EntityTags.of(entry.version()))
                .body(financialEntryWebMapper.toResponse(entry, getFinancialEntryUseCase));
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
        PagedResult<FinancialEntrySummaryView> result =
                listFinancialEntriesUseCase.execute(filter, PageQuery.of(page, size));
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<FinancialEntryResponse> getEntry(UUID id) {
        FinancialEntry entry = getFinancialEntryUseCase.getById(id);
        return ResponseEntity.ok()
                .eTag(EntityTags.of(entry.version()))
                .body(financialEntryWebMapper.toResponse(entry, getFinancialEntryUseCase));
    }

    @Override
    public ResponseEntity<SettlementResponse> settleEntry(
            UUID id,
            SettleFinancialEntryRequest request,
            String ifMatch
    ) {
        FinancialEntry current = getFinancialEntryUseCase.getById(id);
        EntityTags.requireMatch(ifMatch, current.version());
        SettlementCommand command = financialEntryWebMapper.toSettlementCommand(request, current, clock);
        Long expectedVersion = financialEntryWebMapper.extractExpectedVersion(request);
        SettlementOutcome outcome = settleFinancialEntryUseCase.execute(id, command, expectedVersion);
        return ResponseEntity.ok(financialEntryWebMapper.toSettlementResponse(outcome, getFinancialEntryUseCase));
    }

    @Override
    public ResponseEntity<Void> deleteEntryLogically(UUID id, String reason, String ifMatch) {
        if (ifMatch != null && !ifMatch.isBlank()) {
            EntityTags.requireMatch(ifMatch, getFinancialEntryUseCase.getById(id).version());
        }
        cancelFinancialEntryUseCase.execute(id, financialEntryWebMapper.validateCancellationReason(reason));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<FinancialEntryLineageNode>> getEntrySplits(UUID id) {
        return ResponseEntity.ok(getFinancialEntryLineageUseCase.execute(id));
    }
}
