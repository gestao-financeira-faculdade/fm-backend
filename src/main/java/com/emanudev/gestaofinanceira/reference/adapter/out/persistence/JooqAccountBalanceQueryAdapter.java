package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.application.AccountBalanceQueryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component
public class JooqAccountBalanceQueryAdapter implements AccountBalanceQueryPort {

    private static final Table<Record> FINANCIAL_ENTRIES = DSL.table(DSL.name("financial_entries"));

    private final DSLContext dsl;

    public JooqAccountBalanceQueryAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Map<UUID, BigDecimal> settledNetByAccount(Collection<UUID> accountIds, LocalDate asOf) {
        Field<UUID> accountId = DSL.field(DSL.name("account_id"), UUID.class);
        Field<String> status = DSL.field(DSL.name("status"), String.class);
        Field<BigDecimal> amount = DSL.coalesce(
                DSL.field(DSL.name("settled_amount"), BigDecimal.class),
                DSL.field(DSL.name("original_amount"), BigDecimal.class)
        );
        Field<BigDecimal> signed = DSL.when(status.eq("RECEIVED"), amount)
                .when(status.eq("PAID"), amount.neg())
                .otherwise(BigDecimal.ZERO);
        Field<LocalDate> movementDate = DSL.coalesce(
                DSL.field(DSL.name("settlement_date"), LocalDate.class),
                DSL.field(DSL.name("due_date"), LocalDate.class)
        );
        Field<BigDecimal> net = DSL.coalesce(DSL.sum(signed), BigDecimal.ZERO).as("net");

        Map<UUID, BigDecimal> result = new HashMap<>();
        dsl.select(accountId, net)
                .from(FINANCIAL_ENTRIES)
                .where(accountId.in(accountIds))
                .and(status.in("RECEIVED", "PAID"))
                .and(movementDate.le(asOf))
                .groupBy(accountId)
                .fetch()
                .forEach(row -> result.put(row.get(accountId), row.get(net)));
        return result;
    }
}
