package com.emanudev.gestaofinanceira.entry.adapter.in.web;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryFilter;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryLineageNode;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntrySummaryView;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryUseCaseService;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryUseCaseService.CreateEntryCommand;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.SettlementCommand;
import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FinancialEntryController implements FinancialEntryControllerOpenApi {

    private final FinancialEntryUseCaseService entryService;
    private final Clock clock;

    public FinancialEntryController(FinancialEntryUseCaseService entryService, Clock clock) {
        this.entryService = entryService;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<FinancialEntryResponse> createEntry(
            CreateFinancialEntryRequest request,
            String idempotencyKeyHeader
    ) {
        String idempotencyKey = (request.idempotencyKey() != null && !request.idempotencyKey().isBlank())
                ? request.idempotencyKey()
                : idempotencyKeyHeader;

        BigDecimal resolvedAmount = request.originalAmount() != null ? request.originalAmount() : request.amount();
        if (resolvedAmount == null) {
            throw new BusinessRuleViolationException("INVALID_ORIGINAL_AMOUNT", "originalAmount must not be null");
        }
        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency()
                : Money.DEFAULT_CURRENCY;

        FinancialEntry created = entryService.createEntry(new CreateEntryCommand(
                idempotencyKey,
                request.accountId(),
                request.categoryId(),
                request.counterpartyId(),
                request.entryType(),
                request.description(),
                request.competenceDate(),
                request.dueDate(),
                Money.of(resolvedAmount, currency)
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
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
                entryType,
                status,
                categoryId,
                accountId,
                dueDateFrom,
                dueDateTo,
                search
        );
        PagedResult<FinancialEntrySummaryView> result = entryService.findEntries(filter, PageQuery.of(page, size));
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<FinancialEntryResponse> getEntry(UUID id) {
        FinancialEntry entry = entryService.getEntryById(id);
        return ResponseEntity.ok(toResponse(entry));
    }

    @Override
    public ResponseEntity<SettlementResponse> settleEntry(
            UUID id,
            SettleFinancialEntryRequest request
    ) {
        FinancialEntry current = entryService.getEntryById(id);
        String currency = current.originalAmount().currency();

        BigDecimal settledPrincipal = extractSettledPrincipal(request, current.originalAmount().amount());
        Money principalMoney = Money.of(settledPrincipal, currency);

        Money discount = request != null && request.discountAmount() != null
                ? Money.of(request.discountAmount(), currency)
                : Money.zero(currency);
        Money interest = request != null && request.interestAmount() != null
                ? Money.of(request.interestAmount(), currency)
                : Money.zero(currency);
        Money lateFee = request != null && request.lateFeeAmount() != null
                ? Money.of(request.lateFeeAmount(), currency)
                : Money.zero(currency);
        LocalDate settlementDate = request != null && request.settlementDate() != null
                ? request.settlementDate()
                : LocalDate.now(clock);

        boolean createResidual = request == null
                || request.createResidualEntryOnUnderpayment() == null
                || request.createResidualEntryOnUnderpayment();

        Long expectedVersion = request != null ? request.version() : null;

        SettlementCommand command = new SettlementCommand(
                principalMoney,
                discount,
                interest,
                lateFee,
                settlementDate,
                createResidual
        );

        SettlementOutcome outcome = entryService.settleEntry(id, command, expectedVersion);
        FinancialEntryResponse settledResp = toResponse(outcome.settledEntry());
        FinancialEntryResponse residualResp = outcome.residualChildEntry().map(this::toResponse).orElse(null);

        return ResponseEntity.ok(SettlementResponse.from(settledResp, residualResp));
    }

    @Override
    public ResponseEntity<FinancialEntryResponse> cancelEntry(
            UUID id,
            CancelFinancialEntryRequest request,
            String reason
    ) {
        String resolvedReason = resolveCancellationReason(request, reason);
        FinancialEntry cancelled = entryService.cancelEntry(id, resolvedReason);
        return ResponseEntity.ok(toResponse(cancelled));
    }

    @Override
    public ResponseEntity<FinancialEntryResponse> deleteEntryLogically(
            UUID id,
            CancelFinancialEntryRequest request,
            String reason
    ) {
        String resolvedReason = resolveCancellationReason(request, reason);
        FinancialEntry cancelled = entryService.cancelEntry(id, resolvedReason);
        return ResponseEntity.ok(toResponse(cancelled));
    }

    @Override
    public ResponseEntity<List<FinancialEntryLineageNode>> getEntrySplits(UUID id) {
        return ResponseEntity.ok(entryService.getEntryLineage(id));
    }

    private static BigDecimal extractSettledPrincipal(SettleFinancialEntryRequest request, BigDecimal fallbackFullAmount) {
        if (request == null) {
            return fallbackFullAmount;
        }
        if (request.settledPrincipalAmount() != null) {
            return request.settledPrincipalAmount();
        }
        if (request.settledAmount() != null) {
            return request.settledAmount();
        }
        if (request.amount() != null) {
            return request.amount();
        }
        return fallbackFullAmount;
    }

    private static String resolveCancellationReason(CancelFinancialEntryRequest request, String reasonParam) {
        if (request != null) {
            if (request.reason() != null && !request.reason().isBlank()) {
                return request.reason();
            }
            if (request.cancellationReason() != null && !request.cancellationReason().isBlank()) {
                return request.cancellationReason();
            }
        }
        return reasonParam;
    }

    private FinancialEntryResponse toResponse(FinancialEntry entry) {
        FinancialEntrySummaryView summary = null;
        try {
            summary = entryService.getEntrySummaryById(entry.id());
        } catch (Exception ignored) {
            // fallback to domain fields if summary is not available
        }
        return FinancialEntryResponse.from(entry, summary);
    }

    public record CreateFinancialEntryRequest(
            String idempotencyKey,
            UUID accountId,
            @NotNull UUID categoryId,
            UUID counterpartyId,
            @NotNull EntryType entryType,
            @NotBlank @Size(max = 255) String description,
            LocalDate competenceDate,
            @NotNull LocalDate dueDate,
            BigDecimal originalAmount,
            BigDecimal amount,
            String currency
    ) {
    }

    public record SettleFinancialEntryRequest(
            BigDecimal settledPrincipalAmount,
            BigDecimal settledAmount,
            BigDecimal amount,
            BigDecimal discountAmount,
            BigDecimal interestAmount,
            BigDecimal lateFeeAmount,
            LocalDate settlementDate,
            Boolean createResidualEntryOnUnderpayment,
            Long version
    ) {
    }

    public record CancelFinancialEntryRequest(
            String reason,
            String cancellationReason
    ) {
    }

    public record FinancialEntryResponse(
            UUID id,
            String idempotencyKey,
            UUID parentEntryId,
            UUID rootEntryId,
            UUID accountId,
            String accountName,
            UUID categoryId,
            String categoryName,
            boolean categoryRequiresAttachment,
            UUID counterpartyId,
            String counterpartyName,
            EntryType entryType,
            EntryStatus status,
            String description,
            LocalDate competenceDate,
            LocalDate dueDate,
            LocalDate settlementDate,
            BigDecimal originalAmount,
            BigDecimal settledAmount,
            BigDecimal discountAmount,
            BigDecimal interestAmount,
            BigDecimal lateFeeAmount,
            String currency,
            int attachmentCount,
            String cancellationReason,
            Instant cancelledAt,
            UUID cancelledBy,
            UUID createdBy,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static FinancialEntryResponse from(FinancialEntry entry, FinancialEntrySummaryView summary) {
            return new FinancialEntryResponse(
                    entry.id(),
                    entry.idempotencyKey(),
                    entry.parentEntryId(),
                    entry.rootEntryId(),
                    entry.accountId(),
                    summary != null ? summary.accountName() : null,
                    entry.categoryId(),
                    summary != null ? summary.categoryName() : null,
                    summary != null && summary.categoryRequiresAttachment(),
                    entry.counterpartyId(),
                    summary != null ? summary.counterpartyName() : null,
                    entry.entryType(),
                    entry.status(),
                    entry.description(),
                    entry.competenceDate(),
                    entry.dueDate(),
                    entry.settlementDate(),
                    entry.originalAmount().amount(),
                    entry.settledAmount() != null ? entry.settledAmount().amount() : null,
                    entry.discountAmount().amount(),
                    entry.interestAmount().amount(),
                    entry.lateFeeAmount().amount(),
                    entry.originalAmount().currency(),
                    summary != null ? summary.attachmentCount() : 0,
                    entry.cancellationReason(),
                    entry.cancelledAt(),
                    entry.cancelledBy(),
                    entry.createdBy(),
                    entry.version(),
                    entry.createdAt(),
                    entry.updatedAt()
            );
        }
    }

    public record SettlementResponse(
            UUID id,
            UUID parentEntryId,
            UUID rootEntryId,
            EntryType entryType,
            EntryStatus status,
            String description,
            LocalDate dueDate,
            LocalDate settlementDate,
            BigDecimal originalAmount,
            BigDecimal settledAmount,
            BigDecimal discountAmount,
            BigDecimal interestAmount,
            BigDecimal lateFeeAmount,
            String currency,
            long version,
            boolean hasResidualChild,
            FinancialEntryResponse settledEntry,
            FinancialEntryResponse residualChildEntry
    ) {
        public static SettlementResponse from(
                FinancialEntryResponse settledEntry,
                FinancialEntryResponse residualChildEntry
        ) {
            return new SettlementResponse(
                    settledEntry.id(),
                    settledEntry.parentEntryId(),
                    settledEntry.rootEntryId(),
                    settledEntry.entryType(),
                    settledEntry.status(),
                    settledEntry.description(),
                    settledEntry.dueDate(),
                    settledEntry.settlementDate(),
                    settledEntry.originalAmount(),
                    settledEntry.settledAmount(),
                    settledEntry.discountAmount(),
                    settledEntry.interestAmount(),
                    settledEntry.lateFeeAmount(),
                    settledEntry.currency(),
                    settledEntry.version(),
                    residualChildEntry != null,
                    settledEntry,
                    residualChildEntry
            );
        }
    }
}
