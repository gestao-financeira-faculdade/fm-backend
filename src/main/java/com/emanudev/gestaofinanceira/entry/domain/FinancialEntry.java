package com.emanudev.gestaofinanceira.entry.domain;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public record FinancialEntry(
        UUID id,
        String idempotencyKey,
        UUID parentEntryId,
        UUID rootEntryId,
        UUID accountId,
        UUID categoryId,
        UUID counterpartyId,
        EntryType entryType,
        EntryStatus status,
        String description,
        LocalDate competenceDate,
        LocalDate dueDate,
        LocalDate settlementDate,
        Money originalAmount,
        Money settledAmount,
        Money discountAmount,
        Money interestAmount,
        Money lateFeeAmount,
        String cancellationReason,
        Instant cancelledAt,
        UUID cancelledBy,
        UUID createdBy,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static final BigDecimal FISCAL_ATTACHMENT_THRESHOLD = new BigDecimal("5000.00");
    public static final int MIN_CANCELLATION_REASON_LENGTH = 5;

    public FinancialEntry {
        if (id == null) {
            throw new BusinessRuleViolationException("INVALID_ENTRY_ID", "Entry id must not be null");
        }
        if (accountId == null) {
            throw new BusinessRuleViolationException("INVALID_ACCOUNT_ID", "Account id must not be null");
        }
        if (categoryId == null) {
            throw new BusinessRuleViolationException("INVALID_CATEGORY_ID", "Category id must not be null");
        }
        if (entryType == null) {
            throw new BusinessRuleViolationException("INVALID_ENTRY_TYPE", "Entry type must not be null");
        }
        if (status == null) {
            throw new BusinessRuleViolationException("INVALID_ENTRY_STATUS", "Entry status must not be null");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_DESCRIPTION", "Description must not be blank");
        }
        if (competenceDate == null) {
            throw new BusinessRuleViolationException("INVALID_COMPETENCE_DATE", "Competence date must not be null");
        }
        if (dueDate == null) {
            throw new BusinessRuleViolationException("INVALID_DUE_DATE", "Due date must not be null");
        }
        if (originalAmount == null || !originalAmount.isPositive()) {
            throw new BusinessRuleViolationException(
                    "INVALID_ORIGINAL_AMOUNT",
                    "Original amount must be strictly positive (> 0)"
            );
        }
        String currency = originalAmount.currency();
        discountAmount = discountAmount != null ? discountAmount : Money.zero(currency);
        interestAmount = interestAmount != null ? interestAmount : Money.zero(currency);
        lateFeeAmount = lateFeeAmount != null ? lateFeeAmount : Money.zero(currency);
        description = description.trim();
        idempotencyKey = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim();
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : createdAt;
    }

    public static FinancialEntry create(
            String idempotencyKey,
            UUID accountId,
            UUID categoryId,
            UUID counterpartyId,
            EntryType entryType,
            String description,
            LocalDate competenceDate,
            LocalDate dueDate,
            Money originalAmount,
            UUID createdBy,
            LocalDate today,
            Instant now
    ) {
        if (today == null) {
            throw new BusinessRuleViolationException("INVALID_CURRENT_DATE", "Current date (today) must not be null");
        }
        if (originalAmount == null || !originalAmount.isPositive()) {
            throw new BusinessRuleViolationException(
                    "INVALID_ORIGINAL_AMOUNT",
                    "Original amount must be strictly positive (> 0)"
            );
        }
        UUID newId = UUID.randomUUID();
        EntryStatus initialStatus = dueDate.isBefore(today) ? EntryStatus.OVERDUE : EntryStatus.PENDING;
        Instant timestamp = now != null ? now : Instant.now();
        String currency = originalAmount.currency();

        return new FinancialEntry(
                newId,
                idempotencyKey,
                null,
                newId,
                accountId,
                categoryId,
                counterpartyId,
                entryType,
                initialStatus,
                description,
                competenceDate,
                dueDate,
                null,
                originalAmount,
                null,
                Money.zero(currency),
                Money.zero(currency),
                Money.zero(currency),
                null,
                null,
                null,
                createdBy,
                0L,
                timestamp,
                timestamp
        );
    }

    public FinancialEntry markOverdue(LocalDate today) {
        return markOverdue(today, Instant.now());
    }

    public FinancialEntry markOverdue(LocalDate today, Instant now) {
        if (today == null) {
            throw new BusinessRuleViolationException("INVALID_CURRENT_DATE", "Current date (today) must not be null");
        }
        if (status == EntryStatus.OVERDUE) {
            return this;
        }
        if (status != EntryStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "INVALID_STATUS_TRANSITION",
                    "Cannot mark entry as OVERDUE from status " + status
            );
        }
        if (!dueDate.isBefore(today)) {
            return this;
        }
        Instant timestamp = now != null ? now : Instant.now();
        return new FinancialEntry(
                id,
                idempotencyKey,
                parentEntryId,
                rootEntryId,
                accountId,
                categoryId,
                counterpartyId,
                entryType,
                EntryStatus.OVERDUE,
                description,
                competenceDate,
                dueDate,
                settlementDate,
                originalAmount,
                settledAmount,
                discountAmount,
                interestAmount,
                lateFeeAmount,
                cancellationReason,
                cancelledAt,
                cancelledBy,
                createdBy,
                version,
                createdAt,
                timestamp
        );
    }

    public SettlementOutcome settle(
            SettlementCommand command,
            boolean categoryRequiresAttachment,
            String categoryName,
            Money rootOriginalAmount,
            int attachmentCount,
            LocalDate today,
            Instant now
    ) {
        if (command == null) {
            throw new BusinessRuleViolationException("INVALID_SETTLEMENT_COMMAND", "Settlement command must not be null");
        }
        if (!status.isOpen()) {
            throw new BusinessRuleViolationException(
                    "IMMUTABLE_ENTRY_STATUS",
                    "Cannot settle financial entry in status " + status
            );
        }

        Money settledPrincipal = command.settledPrincipalAmount();
        if (!settledPrincipal.isPositive() || settledPrincipal.isGreaterThan(originalAmount)) {
            throw new BusinessRuleViolationException(
                    "INVALID_SETTLEMENT_AMOUNT",
                    "Settled principal amount (%s) must be > 0 and <= original amount (%s)"
                            .formatted(settledPrincipal.amount(), originalAmount.amount())
            );
        }

        Money effectiveRootOriginalAmount = rootOriginalAmount != null ? rootOriginalAmount : originalAmount;
        validateFiscalAttachmentRule(categoryRequiresAttachment, categoryName, effectiveRootOriginalAmount, attachmentCount);

        Money netSettledAmount = settledPrincipal
                .add(command.interestAmount())
                .add(command.lateFeeAmount())
                .subtract(command.discountAmount());

        if (!netSettledAmount.isPositive()) {
            throw new BusinessRuleViolationException(
                    "INVALID_SETTLEMENT_AMOUNT",
                    "Net settled amount after interest, late fee and discount must be strictly positive"
            );
        }

        Instant timestamp = now != null ? now : Instant.now();
        LocalDate currentDay = today != null ? today : command.settlementDate();
        EntryStatus terminalStatus = EntryStatus.terminalStatusFor(entryType);

        if (settledPrincipal.amount().compareTo(originalAmount.amount()) == 0) {
            FinancialEntry fullySettled = new FinancialEntry(
                    id,
                    idempotencyKey,
                    parentEntryId,
                    rootEntryId != null ? rootEntryId : id,
                    accountId,
                    categoryId,
                    counterpartyId,
                    entryType,
                    terminalStatus,
                    description,
                    competenceDate,
                    dueDate,
                    command.settlementDate(),
                    originalAmount,
                    netSettledAmount,
                    command.discountAmount(),
                    command.interestAmount(),
                    command.lateFeeAmount(),
                    cancellationReason,
                    cancelledAt,
                    cancelledBy,
                    createdBy,
                    version,
                    createdAt,
                    timestamp
            );
            return new SettlementOutcome(fullySettled, Optional.empty());
        }

        if (!command.createResidualEntryOnUnderpayment()) {
            throw new BusinessRuleViolationException(
                    "UNDERPAYMENT_REQUIRES_SPLIT",
                    "Underpayment requires createResidualEntryOnUnderpayment=true to generate residual split entry"
            );
        }

        UUID effectiveRootId = rootEntryId != null ? rootEntryId : id;
        FinancialEntry settledParent = new FinancialEntry(
                id,
                idempotencyKey,
                parentEntryId,
                effectiveRootId,
                accountId,
                categoryId,
                counterpartyId,
                entryType,
                terminalStatus,
                description,
                competenceDate,
                dueDate,
                command.settlementDate(),
                originalAmount,
                netSettledAmount,
                command.discountAmount(),
                command.interestAmount(),
                command.lateFeeAmount(),
                cancellationReason,
                cancelledAt,
                cancelledBy,
                createdBy,
                version,
                createdAt,
                timestamp
        );

        Money residualAmount = originalAmount.subtract(settledPrincipal);
        EntryStatus childInitialStatus = dueDate.isBefore(currentDay) ? EntryStatus.OVERDUE : EntryStatus.PENDING;
        String currency = originalAmount.currency();

        FinancialEntry residualChild = new FinancialEntry(
                UUID.randomUUID(),
                null,
                this.id,
                effectiveRootId,
                accountId,
                categoryId,
                counterpartyId,
                entryType,
                childInitialStatus,
                description + " (Saldo Residual)",
                competenceDate,
                dueDate,
                null,
                residualAmount,
                null,
                Money.zero(currency),
                Money.zero(currency),
                Money.zero(currency),
                null,
                null,
                null,
                createdBy,
                0L,
                timestamp,
                timestamp
        );

        return new SettlementOutcome(settledParent, Optional.of(residualChild));
    }

    private void validateFiscalAttachmentRule(
            boolean categoryRequiresAttachment,
            String categoryName,
            Money rootOriginalAmount,
            int attachmentCount
    ) {
        if (entryType != EntryType.PAYABLE) {
            return;
        }
        boolean isTaxCategory = categoryRequiresAttachment
                || (categoryName != null && "impostos".equalsIgnoreCase(categoryName.trim()));

        Money threshold = Money.of(FISCAL_ATTACHMENT_THRESHOLD, originalAmount.currency());
        boolean exceedsThreshold = originalAmount.isGreaterThan(threshold)
                || (rootOriginalAmount != null && rootOriginalAmount.isGreaterThan(threshold));

        if ((isTaxCategory || exceedsThreshold) && attachmentCount <= 0) {
            throw new FiscalAttachmentRequiredException(
                    "Fiscal attachment (PDF, JPG or PNG) is mandatory for PAYABLE entries in 'Impostos' category or with original/root amount above 5000.00"
            );
        }
    }

    public FinancialEntry cancel(String reason, UUID actorId, Instant now) {
        return cancel(reason, actorId, now, false);
    }

    public FinancialEntry cancel(String reason, UUID actorId, Instant now, boolean hasActiveChildren) {
        if (status == EntryStatus.CANCELLED) {
            throw new BusinessRuleViolationException("ENTRY_ALREADY_CANCELLED", "Financial entry is already CANCELLED");
        }
        if (status.isSettled()) {
            throw new BusinessRuleViolationException(
                    "CANNOT_CANCEL_SETTLED_ENTRY",
                    "Cannot cancel a settled financial entry (" + status + ")"
            );
        }
        if (hasActiveChildren) {
            throw new BusinessRuleViolationException(
                    "ENTRY_HAS_ACTIVE_CHILDREN",
                    "Cannot cancel financial entry because it has active child split entries"
            );
        }
        if (reason == null || reason.trim().length() < MIN_CANCELLATION_REASON_LENGTH) {
            throw new BusinessRuleViolationException(
                    "INVALID_CANCELLATION_REASON",
                    "Cancellation reason is mandatory and must have at least " + MIN_CANCELLATION_REASON_LENGTH + " characters"
            );
        }
        Instant timestamp = now != null ? now : Instant.now();
        return new FinancialEntry(
                id,
                idempotencyKey,
                parentEntryId,
                rootEntryId,
                accountId,
                categoryId,
                counterpartyId,
                entryType,
                EntryStatus.CANCELLED,
                description,
                competenceDate,
                dueDate,
                settlementDate,
                originalAmount,
                settledAmount,
                discountAmount,
                interestAmount,
                lateFeeAmount,
                reason.trim(),
                timestamp,
                actorId,
                createdBy,
                version,
                createdAt,
                timestamp
        );
    }
}
