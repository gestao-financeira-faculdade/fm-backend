package com.emanudev.gestaofinanceira.simulation.adapter.out.persistence;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.simulation.application.port.out.CashFlowProjectionPort;
import com.emanudev.gestaofinanceira.simulation.domain.model.ScheduledDailyCashFlow;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record4;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JooqCashFlowProjectionAdapter implements CashFlowProjectionPort {

    private static final org.jooq.Table<Record> FINANCIAL_ACCOUNTS = DSL.table(DSL.name("financial_accounts"));
    private static final org.jooq.Table<Record> FINANCIAL_ENTRIES = DSL.table(DSL.name("financial_entries"));
    private static final org.jooq.Table<Record> CATEGORIES = DSL.table(DSL.name("categories"));

    private final DSLContext dsl;

    public JooqCashFlowProjectionAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public Money calculateCurrentConsolidatedBalance() {
        BigDecimal totalInitialBalance = dsl.select(
                        DSL.coalesce(DSL.sum(DSL.field(DSL.name("initial_balance"), BigDecimal.class)), BigDecimal.ZERO)
                )
                .from(FINANCIAL_ACCOUNTS)
                .where(DSL.field(DSL.name("is_active"), Boolean.class).eq(true))
                .fetchOneInto(BigDecimal.class);

        org.jooq.Field<BigDecimal> effectiveSettledAmount = DSL.coalesce(
                DSL.field(DSL.name("fe", "settled_amount"), BigDecimal.class),
                DSL.field(DSL.name("fe", "original_amount"), BigDecimal.class),
                DSL.val(BigDecimal.ZERO)
        );

        BigDecimal totalReceived = dsl.select(
                        DSL.coalesce(DSL.sum(effectiveSettledAmount), BigDecimal.ZERO)
                )
                .from(FINANCIAL_ENTRIES.as("fe"))
                .join(FINANCIAL_ACCOUNTS.as("fa"))
                .on(DSL.field(DSL.name("fe", "account_id")).eq(DSL.field(DSL.name("fa", "id"))))
                .where(DSL.field(DSL.name("fa", "is_active"), Boolean.class).eq(true))
                .and(DSL.field(DSL.name("fe", "entry_type"), String.class).eq("RECEIVABLE"))
                .and(DSL.field(DSL.name("fe", "status"), String.class).eq("RECEIVED"))
                .fetchOneInto(BigDecimal.class);

        BigDecimal totalPaid = dsl.select(
                        DSL.coalesce(DSL.sum(effectiveSettledAmount), BigDecimal.ZERO)
                )
                .from(FINANCIAL_ENTRIES.as("fe"))
                .join(FINANCIAL_ACCOUNTS.as("fa"))
                .on(DSL.field(DSL.name("fe", "account_id")).eq(DSL.field(DSL.name("fa", "id"))))
                .where(DSL.field(DSL.name("fa", "is_active"), Boolean.class).eq(true))
                .and(DSL.field(DSL.name("fe", "entry_type"), String.class).eq("PAYABLE"))
                .and(DSL.field(DSL.name("fe", "status"), String.class).eq("PAID"))
                .fetchOneInto(BigDecimal.class);

        BigDecimal safeInitial = totalInitialBalance != null ? totalInitialBalance : BigDecimal.ZERO;
        BigDecimal safeReceived = totalReceived != null ? totalReceived : BigDecimal.ZERO;
        BigDecimal safePaid = totalPaid != null ? totalPaid : BigDecimal.ZERO;

        return Money.brl(safeInitial.add(safeReceived).subtract(safePaid));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduledDailyCashFlow> projectScheduledCashFlows(LocalDate referenceDate, int horizonDays) {
        LocalDate endDate = referenceDate.plusDays(horizonDays);

        var dueDateField = DSL.field(DSL.name("fe", "due_date"), LocalDate.class);
        var entryTypeField = DSL.field(DSL.name("fe", "entry_type"), String.class);
        var isFixedCostField = DSL.field(DSL.name("c", "is_fixed_cost"), Boolean.class);
        var sumAmountField = DSL.coalesce(
                DSL.sum(DSL.field(DSL.name("fe", "original_amount"), BigDecimal.class)),
                BigDecimal.ZERO
        ).as("total_amount");

        Result<Record4<LocalDate, String, Boolean, BigDecimal>> rows = dsl
                .select(dueDateField, entryTypeField, isFixedCostField, sumAmountField)
                .from(FINANCIAL_ENTRIES.as("fe"))
                .join(CATEGORIES.as("c"))
                .on(DSL.field(DSL.name("fe", "category_id")).eq(DSL.field(DSL.name("c", "id"))))
                .where(DSL.field(DSL.name("fe", "status"), String.class).in("PENDING", "OVERDUE"))
                .and(dueDateField.le(endDate))
                .groupBy(dueDateField, entryTypeField, isFixedCostField)
                .fetch();

        Map<Integer, BigDecimal> fixedByDay = new HashMap<>();
        Map<Integer, BigDecimal> variableByDay = new HashMap<>();
        Map<Integer, BigDecimal> receivableByDay = new HashMap<>();

        for (Record4<LocalDate, String, Boolean, BigDecimal> row : rows) {
            LocalDate dueDate = row.value1();
            String entryType = row.value2();
            boolean isFixed = Boolean.TRUE.equals(row.value3());
            BigDecimal amount = row.value4() != null ? row.value4() : BigDecimal.ZERO;

            long rawOffset = ChronoUnit.DAYS.between(referenceDate, dueDate);
            int dayOffset = (int) Math.max(1L, Math.min(horizonDays, rawOffset));

            if ("RECEIVABLE".equalsIgnoreCase(entryType)) {
                receivableByDay.merge(dayOffset, amount, BigDecimal::add);
            } else if ("PAYABLE".equalsIgnoreCase(entryType)) {
                if (isFixed) {
                    fixedByDay.merge(dayOffset, amount, BigDecimal::add);
                } else {
                    variableByDay.merge(dayOffset, amount, BigDecimal::add);
                }
            }
        }

        List<ScheduledDailyCashFlow> schedule = new ArrayList<>(horizonDays);
        for (int day = 1; day <= horizonDays; day++) {
            Money payableFixed = Money.brl(fixedByDay.getOrDefault(day, BigDecimal.ZERO));
            Money payableVariable = Money.brl(variableByDay.getOrDefault(day, BigDecimal.ZERO));
            Money receivable = Money.brl(receivableByDay.getOrDefault(day, BigDecimal.ZERO));
            schedule.add(new ScheduledDailyCashFlow(day, payableFixed, payableVariable, receivable));
        }

        return schedule;
    }
}
