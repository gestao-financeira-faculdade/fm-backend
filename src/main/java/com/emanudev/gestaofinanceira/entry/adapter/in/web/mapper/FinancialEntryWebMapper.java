package com.emanudev.gestaofinanceira.entry.adapter.in.web.mapper;

import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.CancelFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.CreateFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.FinancialEntryResponse;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.SettleFinancialEntryRequest;
import com.emanudev.gestaofinanceira.entry.adapter.in.web.dto.SettlementResponse;
import com.emanudev.gestaofinanceira.entry.application.CreateEntryCommand;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntrySummaryView;
import com.emanudev.gestaofinanceira.entry.application.usecase.GetFinancialEntryUseCase;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.SettlementCommand;
import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class FinancialEntryWebMapper {

    public CreateEntryCommand toCreateCommand(CreateFinancialEntryRequest request, String idempotencyKeyHeader) {
        String idempotencyKey = Optional.ofNullable(request.idempotencyKey())
                .filter(k -> !k.isBlank())
                .orElse(idempotencyKeyHeader);

        BigDecimal resolvedAmount = Optional.ofNullable(request.originalAmount()).orElse(request.amount());
        if (resolvedAmount == null) {
            throw new BusinessRuleViolationException("INVALID_ORIGINAL_AMOUNT", "originalAmount must not be null");
        }
        String currency = Optional.ofNullable(request.currency())
                .filter(c -> !c.isBlank())
                .orElse(Money.DEFAULT_CURRENCY);

        return new CreateEntryCommand(
                idempotencyKey,
                request.accountId(),
                request.categoryId(),
                request.counterpartyId(),
                request.entryType(),
                request.description(),
                request.competenceDate(),
                request.dueDate(),
                Money.of(resolvedAmount, currency)
        );
    }

    public SettlementCommand toSettlementCommand(
            SettleFinancialEntryRequest request,
            FinancialEntry current,
            Clock clock
    ) {
        String currency = current.originalAmount().currency();
        return new SettlementCommand(
                Money.of(request.paidAmount(), currency),
                moneyOrZero(request.discountAmount(), currency),
                moneyOrZero(request.interestAmount(), currency),
                moneyOrZero(request.lateFeeAmount(), currency),
                Optional.ofNullable(request.settlementDate()).orElseGet(() -> LocalDate.now(clock)),
                Boolean.TRUE.equals(request.allowPartial())
        );
    }

    public long extractExpectedVersion(SettleFinancialEntryRequest request) {
        return request.expectedVersion();
    }

    public String resolveCancellationReason(CancelFinancialEntryRequest request, String reasonParam) {
        String bodyReason = Optional.ofNullable(request).map(CancelFinancialEntryRequest::reason).orElse(null);
        String bodyCancelReason = Optional.ofNullable(request).map(CancelFinancialEntryRequest::cancellationReason).orElse(null);
        return Stream.of(bodyReason, bodyCancelReason, reasonParam)
                .filter(val -> val != null && !val.isBlank())
                .findFirst()
                .orElse(reasonParam);
    }

    public FinancialEntryResponse toResponse(FinancialEntry entry, GetFinancialEntryUseCase getFinancialEntryUseCase) {
        FinancialEntrySummaryView summary = null;
        try {
            summary = getFinancialEntryUseCase.getSummaryById(entry.id());
        } catch (Exception ignored) {
            // fallback to domain fields if summary is not available
        }
        return FinancialEntryResponse.from(entry, summary);
    }

    public SettlementResponse toSettlementResponse(
            SettlementOutcome outcome,
            GetFinancialEntryUseCase getFinancialEntryUseCase
    ) {
        FinancialEntryResponse settledResp = toResponse(outcome.settledEntry(), getFinancialEntryUseCase);
        FinancialEntryResponse residualResp = outcome.residualChildEntry()
                .map(child -> toResponse(child, getFinancialEntryUseCase))
                .orElse(null);
        return SettlementResponse.from(settledResp, residualResp);
    }

    private static Money moneyOrZero(BigDecimal amount, String currency) {
        return amount != null ? Money.of(amount, currency) : Money.zero(currency);
    }
}
