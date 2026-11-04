package com.emanudev.gestaofinanceira.entry.adapter.out.persistence;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryFilter;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryLineageNode;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryQueryPort;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntrySummaryView;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JooqFinancialEntryQueryAdapter implements FinancialEntryQueryPort {

    private static final Table<Record> FE = DSL.table(DSL.name("financial_entries")).as("fe");
    private static final Table<Record> FA = DSL.table(DSL.name("financial_accounts")).as("fa");
    private static final Table<Record> C = DSL.table(DSL.name("categories")).as("c");
    private static final Table<Record> CP = DSL.table(DSL.name("counterparties")).as("cp");
    private static final Table<Record> FAT = DSL.table(DSL.name("fiscal_attachments")).as("fat");

    private static final Field<UUID> FE_ID = DSL.field(DSL.name("fe", "id"), UUID.class);
    private static final Field<String> FE_IDEMPOTENCY_KEY = DSL.field(DSL.name("fe", "idempotency_key"), String.class);
    private static final Field<UUID> FE_PARENT_ID = DSL.field(DSL.name("fe", "parent_entry_id"), UUID.class);
    private static final Field<UUID> FE_ROOT_ID = DSL.field(DSL.name("fe", "root_entry_id"), UUID.class);
    private static final Field<UUID> FE_ACCOUNT_ID = DSL.field(DSL.name("fe", "account_id"), UUID.class);
    private static final Field<UUID> FE_CATEGORY_ID = DSL.field(DSL.name("fe", "category_id"), UUID.class);
    private static final Field<UUID> FE_COUNTERPARTY_ID = DSL.field(DSL.name("fe", "counterparty_id"), UUID.class);
    private static final Field<String> FE_ENTRY_TYPE = DSL.field(DSL.name("fe", "entry_type"), String.class);
    private static final Field<String> FE_STATUS = DSL.field(DSL.name("fe", "status"), String.class);
    private static final Field<String> FE_DESCRIPTION = DSL.field(DSL.name("fe", "description"), String.class);
    private static final Field<LocalDate> FE_COMPETENCE_DATE = DSL.field(DSL.name("fe", "competence_date"), LocalDate.class);
    private static final Field<LocalDate> FE_DUE_DATE = DSL.field(DSL.name("fe", "due_date"), LocalDate.class);
    private static final Field<LocalDate> FE_SETTLEMENT_DATE = DSL.field(DSL.name("fe", "settlement_date"), LocalDate.class);
    private static final Field<BigDecimal> FE_ORIGINAL_AMOUNT = DSL.field(DSL.name("fe", "original_amount"), BigDecimal.class);
    private static final Field<BigDecimal> FE_SETTLED_AMOUNT = DSL.field(DSL.name("fe", "settled_amount"), BigDecimal.class);
    private static final Field<BigDecimal> FE_DISCOUNT_AMOUNT = DSL.field(DSL.name("fe", "discount_amount"), BigDecimal.class);
    private static final Field<BigDecimal> FE_INTEREST_AMOUNT = DSL.field(DSL.name("fe", "interest_amount"), BigDecimal.class);
    private static final Field<BigDecimal> FE_LATE_FEE_AMOUNT = DSL.field(DSL.name("fe", "late_fee_amount"), BigDecimal.class);
    private static final Field<String> FE_CURRENCY = DSL.field(DSL.name("fe", "currency"), String.class);
    private static final Field<Long> FE_VERSION = DSL.field(DSL.name("fe", "version"), Long.class);
    private static final Field<OffsetDateTime> FE_CREATED_AT = DSL.field(DSL.name("fe", "created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> FE_UPDATED_AT = DSL.field(DSL.name("fe", "updated_at"), OffsetDateTime.class);

    private static final Field<UUID> FA_ID = DSL.field(DSL.name("fa", "id"), UUID.class);
    private static final Field<String> FA_NAME = DSL.field(DSL.name("fa", "name"), String.class);

    private static final Field<UUID> C_ID = DSL.field(DSL.name("c", "id"), UUID.class);
    private static final Field<String> C_NAME = DSL.field(DSL.name("c", "name"), String.class);
    private static final Field<Boolean> C_REQUIRES_ATTACHMENT = DSL.field(DSL.name("c", "requires_attachment_always"), Boolean.class);

    private static final Field<UUID> CP_ID = DSL.field(DSL.name("cp", "id"), UUID.class);
    private static final Field<String> CP_LEGAL_NAME = DSL.field(DSL.name("cp", "legal_name"), String.class);

    private static final Field<UUID> FAT_ENTRY_ID = DSL.field(DSL.name("fat", "entry_id"), UUID.class);

    private final DSLContext dsl;

    public JooqFinancialEntryQueryAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public PagedResult<FinancialEntrySummaryView> findEntries(FinancialEntryFilter filter, PageQuery pageQuery) {
        FinancialEntryFilter effectiveFilter = filter != null ? filter : FinancialEntryFilter.empty();
        PageQuery effectivePageQuery = pageQuery != null ? pageQuery : PageQuery.of(0, 20);

        Condition condition = buildFilterCondition(effectiveFilter);

        Long total = dsl.selectCount()
                .from(FE)
                .innerJoin(C).on(C_ID.eq(FE_CATEGORY_ID))
                .innerJoin(FA).on(FA_ID.eq(FE_ACCOUNT_ID))
                .leftJoin(CP).on(CP_ID.eq(FE_COUNTERPARTY_ID))
                .where(condition)
                .fetchOne(0, Long.class);

        long totalElements = total != null ? total : 0L;

        Field<Integer> attachmentCountField = buildAttachmentCountField();

        List<FinancialEntrySummaryView> items = dsl.select(
                        FE_ID,
                        FE_IDEMPOTENCY_KEY,
                        FE_PARENT_ID,
                        FE_ROOT_ID,
                        FE_ACCOUNT_ID,
                        FA_NAME,
                        FE_CATEGORY_ID,
                        C_NAME,
                        C_REQUIRES_ATTACHMENT,
                        FE_COUNTERPARTY_ID,
                        CP_LEGAL_NAME,
                        FE_ENTRY_TYPE,
                        FE_STATUS,
                        FE_DESCRIPTION,
                        FE_COMPETENCE_DATE,
                        FE_DUE_DATE,
                        FE_SETTLEMENT_DATE,
                        FE_ORIGINAL_AMOUNT,
                        FE_SETTLED_AMOUNT,
                        FE_DISCOUNT_AMOUNT,
                        FE_INTEREST_AMOUNT,
                        FE_LATE_FEE_AMOUNT,
                        FE_CURRENCY,
                        attachmentCountField,
                        FE_VERSION,
                        FE_CREATED_AT,
                        FE_UPDATED_AT
                )
                .from(FE)
                .innerJoin(C).on(C_ID.eq(FE_CATEGORY_ID))
                .innerJoin(FA).on(FA_ID.eq(FE_ACCOUNT_ID))
                .leftJoin(CP).on(CP_ID.eq(FE_COUNTERPARTY_ID))
                .where(condition)
                .orderBy(FE_DUE_DATE.desc(), FE_CREATED_AT.desc())
                .limit(effectivePageQuery.size())
                .offset(effectivePageQuery.offset())
                .fetch(r -> mapToSummaryView(r, attachmentCountField));

        return PagedResult.of(items, totalElements, effectivePageQuery.page(), effectivePageQuery.size());
    }

    @Override
    public Optional<FinancialEntrySummaryView> findEntrySummaryById(UUID entryId) {
        if (entryId == null) {
            return Optional.empty();
        }
        Field<Integer> attachmentCountField = buildAttachmentCountField();

        Record record = dsl.select(
                        FE_ID,
                        FE_IDEMPOTENCY_KEY,
                        FE_PARENT_ID,
                        FE_ROOT_ID,
                        FE_ACCOUNT_ID,
                        FA_NAME,
                        FE_CATEGORY_ID,
                        C_NAME,
                        C_REQUIRES_ATTACHMENT,
                        FE_COUNTERPARTY_ID,
                        CP_LEGAL_NAME,
                        FE_ENTRY_TYPE,
                        FE_STATUS,
                        FE_DESCRIPTION,
                        FE_COMPETENCE_DATE,
                        FE_DUE_DATE,
                        FE_SETTLEMENT_DATE,
                        FE_ORIGINAL_AMOUNT,
                        FE_SETTLED_AMOUNT,
                        FE_DISCOUNT_AMOUNT,
                        FE_INTEREST_AMOUNT,
                        FE_LATE_FEE_AMOUNT,
                        FE_CURRENCY,
                        attachmentCountField,
                        FE_VERSION,
                        FE_CREATED_AT,
                        FE_UPDATED_AT
                )
                .from(FE)
                .innerJoin(C).on(C_ID.eq(FE_CATEGORY_ID))
                .innerJoin(FA).on(FA_ID.eq(FE_ACCOUNT_ID))
                .leftJoin(CP).on(CP_ID.eq(FE_COUNTERPARTY_ID))
                .where(FE_ID.eq(entryId))
                .fetchOne();

        return Optional.ofNullable(record).map(r -> mapToSummaryView(r, attachmentCountField));
    }

    @Override
    public List<FinancialEntryLineageNode> getEntryLineage(UUID entryId) {
        if (entryId == null) {
            return List.of();
        }
        Record targetRecord = dsl.select(FE_ID, FE_ROOT_ID)
                .from(FE)
                .where(FE_ID.eq(entryId))
                .fetchOne();

        if (targetRecord == null) {
            return List.of();
        }

        UUID rootId = targetRecord.get(FE_ROOT_ID);
        UUID effectiveRootId = rootId != null ? rootId : targetRecord.get(FE_ID);

        return dsl.select(
                        FE_ID,
                        FE_PARENT_ID,
                        FE_ROOT_ID,
                        FE_ENTRY_TYPE,
                        FE_STATUS,
                        FE_DESCRIPTION,
                        FE_DUE_DATE,
                        FE_SETTLEMENT_DATE,
                        FE_ORIGINAL_AMOUNT,
                        FE_SETTLED_AMOUNT,
                        FE_CURRENCY,
                        FE_CREATED_AT
                )
                .from(FE)
                .where(FE_ID.eq(effectiveRootId).or(FE_ROOT_ID.eq(effectiveRootId)))
                .orderBy(FE_CREATED_AT.asc(), FE_DUE_DATE.asc())
                .fetch(r -> new FinancialEntryLineageNode(
                        r.get(FE_ID),
                        r.get(FE_PARENT_ID),
                        r.get(FE_ROOT_ID),
                        EntryType.valueOf(r.get(FE_ENTRY_TYPE)),
                        EntryStatus.valueOf(r.get(FE_STATUS)),
                        r.get(FE_DESCRIPTION),
                        r.get(FE_DUE_DATE),
                        r.get(FE_SETTLEMENT_DATE),
                        r.get(FE_ORIGINAL_AMOUNT),
                        r.get(FE_SETTLED_AMOUNT),
                        r.get(FE_CURRENCY),
                        toInstant(r.get(FE_CREATED_AT))
                ));
    }

    private Condition buildFilterCondition(FinancialEntryFilter filter) {
        Condition condition = DSL.trueCondition();

        if (filter.entryType() != null) {
            condition = condition.and(FE_ENTRY_TYPE.eq(filter.entryType().name()));
        }
        if (filter.status() != null) {
            condition = condition.and(FE_STATUS.eq(filter.status().name()));
        }
        if (filter.categoryId() != null) {
            condition = condition.and(FE_CATEGORY_ID.eq(filter.categoryId()));
        }
        if (filter.accountId() != null) {
            condition = condition.and(FE_ACCOUNT_ID.eq(filter.accountId()));
        }
        if (filter.dueDateFrom() != null) {
            condition = condition.and(FE_DUE_DATE.ge(filter.dueDateFrom()));
        }
        if (filter.dueDateTo() != null) {
            condition = condition.and(FE_DUE_DATE.le(filter.dueDateTo()));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String pattern = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
            condition = condition.and(
                    DSL.lower(FE_DESCRIPTION).like(pattern)
                            .or(DSL.lower(DSL.coalesce(CP_LEGAL_NAME, "")).like(pattern))
                            .or(DSL.lower(C_NAME).like(pattern))
            );
        }
        return condition;
    }

    private Field<Integer> buildAttachmentCountField() {
        return DSL.field(
                DSL.selectCount()
                        .from(FAT)
                        .where(FAT_ENTRY_ID.eq(FE_ID).or(FAT_ENTRY_ID.eq(FE_ROOT_ID)))
        ).as("attachment_count");
    }

    private FinancialEntrySummaryView mapToSummaryView(Record r, Field<Integer> attachmentCountField) {
        Integer attCount = r.get(attachmentCountField);
        Long version = r.get(FE_VERSION);
        Boolean requiresAttachment = r.get(C_REQUIRES_ATTACHMENT);

        return new FinancialEntrySummaryView(
                r.get(FE_ID),
                r.get(FE_IDEMPOTENCY_KEY),
                r.get(FE_PARENT_ID),
                r.get(FE_ROOT_ID),
                r.get(FE_ACCOUNT_ID),
                r.get(FA_NAME),
                r.get(FE_CATEGORY_ID),
                r.get(C_NAME),
                Boolean.TRUE.equals(requiresAttachment),
                r.get(FE_COUNTERPARTY_ID),
                r.get(CP_LEGAL_NAME),
                EntryType.valueOf(r.get(FE_ENTRY_TYPE)),
                EntryStatus.valueOf(r.get(FE_STATUS)),
                r.get(FE_DESCRIPTION),
                r.get(FE_COMPETENCE_DATE),
                r.get(FE_DUE_DATE),
                r.get(FE_SETTLEMENT_DATE),
                r.get(FE_ORIGINAL_AMOUNT),
                r.get(FE_SETTLED_AMOUNT),
                r.get(FE_DISCOUNT_AMOUNT),
                r.get(FE_INTEREST_AMOUNT),
                r.get(FE_LATE_FEE_AMOUNT),
                r.get(FE_CURRENCY),
                attCount != null ? attCount : 0,
                version != null ? version : 0L,
                toInstant(r.get(FE_CREATED_AT)),
                toInstant(r.get(FE_UPDATED_AT))
        );
    }

    private static Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
