package com.emanudev.gestaofinanceira.reporting.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reporting.application.DailyPercentileBand;
import com.emanudev.gestaofinanceira.reporting.application.DashboardQueryPort;
import com.emanudev.gestaofinanceira.reporting.application.ExecutiveKpiResponse;
import com.emanudev.gestaofinanceira.reporting.application.HistoricalBalancePoint;
import com.emanudev.gestaofinanceira.reporting.application.ProjectedSimulationCone;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.application.ReportRowItem;
import com.emanudev.gestaofinanceira.reporting.application.UncertaintyConeResponse;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class JooqDashboardQueryAdapter implements DashboardQueryPort {

    private static final Table<Record> FINANCIAL_ACCOUNTS = DSL.table(DSL.name("financial_accounts"));
    private static final Table<Record> FINANCIAL_ENTRIES = DSL.table(DSL.name("financial_entries"));
    private static final Table<Record> SIMULATION_RUNS = DSL.table(DSL.name("simulation_runs"));

    private static final Table<Record> FE = FINANCIAL_ENTRIES.as("fe");
    private static final Table<Record> C = DSL.table(DSL.name("categories")).as("c");
    private static final Table<Record> FA = FINANCIAL_ACCOUNTS.as("fa");
    private static final Table<Record> CP = DSL.table(DSL.name("counterparties")).as("cp");

    private final DSLContext dsl;
    private final Clock clock;

    @Autowired
    public JooqDashboardQueryAdapter(DSLContext dsl, Clock clock) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public JooqDashboardQueryAdapter(DSLContext dsl) {
        this(dsl, Clock.systemDefaultZone());
    }

    @Override
    public ExecutiveKpiResponse fetchExecutiveKpis(LocalDate referenceDate) {
        LocalDate effectiveDate = referenceDate != null ? referenceDate : LocalDate.now(clock);

        BigDecimal initialAccountsBalance = fetchActiveAccountsInitialBalance();

        Field<String> statusField = DSL.field(DSL.name("status"), String.class);
        Field<String> entryTypeField = DSL.field(DSL.name("entry_type"), String.class);
        Field<LocalDate> dueDateField = DSL.field(DSL.name("due_date"), LocalDate.class);
        Field<BigDecimal> originalAmountField = DSL.field(DSL.name("original_amount"), BigDecimal.class);
        Field<BigDecimal> settledAmountField = DSL.field(DSL.name("settled_amount"), BigDecimal.class);
        Field<BigDecimal> effectiveSettledAmount = DSL.coalesce(settledAmountField, originalAmountField);

        BigDecimal totalReceived = dsl.select(DSL.coalesce(DSL.sum(effectiveSettledAmount), BigDecimal.ZERO))
                .from(FINANCIAL_ENTRIES)
                .where(statusField.eq("RECEIVED"))
                .fetchOne(0, BigDecimal.class);

        BigDecimal totalPaid = dsl.select(DSL.coalesce(DSL.sum(effectiveSettledAmount), BigDecimal.ZERO))
                .from(FINANCIAL_ENTRIES)
                .where(statusField.eq("PAID"))
                .fetchOne(0, BigDecimal.class);

        BigDecimal consolidatedAmount = nonNull(initialAccountsBalance)
                .add(nonNull(totalReceived))
                .subtract(nonNull(totalPaid));

        BigDecimal receivableTodayAmount = dsl.select(DSL.coalesce(DSL.sum(originalAmountField), BigDecimal.ZERO))
                .from(FINANCIAL_ENTRIES)
                .where(entryTypeField.eq("RECEIVABLE"))
                .and(statusField.in("PENDING", "OVERDUE"))
                .and(dueDateField.le(effectiveDate))
                .fetchOne(0, BigDecimal.class);

        BigDecimal payableTodayAmount = dsl.select(DSL.coalesce(DSL.sum(originalAmountField), BigDecimal.ZERO))
                .from(FINANCIAL_ENTRIES)
                .where(entryTypeField.eq("PAYABLE"))
                .and(statusField.in("PENDING", "OVERDUE"))
                .and(dueDateField.le(effectiveDate))
                .fetchOne(0, BigDecimal.class);

        LocalDate monthStart = effectiveDate.withDayOfMonth(1);
        LocalDate monthEnd = effectiveDate.withDayOfMonth(effectiveDate.lengthOfMonth());

        BigDecimal overdueReceivablesMonth = dsl.select(DSL.coalesce(DSL.sum(originalAmountField), BigDecimal.ZERO))
                .from(FINANCIAL_ENTRIES)
                .where(entryTypeField.eq("RECEIVABLE"))
                .and(statusField.eq("OVERDUE"))
                .and(dueDateField.between(monthStart, monthEnd))
                .fetchOne(0, BigDecimal.class);

        BigDecimal totalValidReceivablesMonth = dsl.select(DSL.coalesce(DSL.sum(originalAmountField), BigDecimal.ZERO))
                .from(FINANCIAL_ENTRIES)
                .where(entryTypeField.eq("RECEIVABLE"))
                .and(statusField.ne("CANCELLED"))
                .and(dueDateField.between(monthStart, monthEnd))
                .fetchOne(0, BigDecimal.class);

        Percentage delinquencyRate = calculateDelinquencyPercentage(
                nonNull(overdueReceivablesMonth),
                nonNull(totalValidReceivablesMonth)
        );

        return new ExecutiveKpiResponse(
                Money.brl(consolidatedAmount),
                Money.brl(nonNull(receivableTodayAmount)),
                Money.brl(nonNull(payableTodayAmount)),
                delinquencyRate,
                effectiveDate,
                Instant.now(clock)
        );
    }

    @Override
    public UncertaintyConeResponse fetchUncertaintyCone(
            LocalDate referenceDate,
            int historyDays,
            Optional<UUID> simulationId
    ) {
        LocalDate effectiveDate = referenceDate != null ? referenceDate : LocalDate.now(clock);
        int effectiveDays = Math.max(1, historyDays);
        LocalDate startDate = effectiveDate.minusDays(effectiveDays - 1L);

        List<HistoricalBalancePoint> historicalSeries = buildHistoricalSeries(startDate, effectiveDate);
        Optional<ProjectedSimulationCone> projectionCone = fetchProjectedCone(effectiveDate, simulationId);

        return new UncertaintyConeResponse(historicalSeries, projectionCone);
    }

    @Override
    public List<ReportRowItem> fetchReportEntries(ReportExportFilter filter) {
        ReportExportFilter effectiveFilter = filter != null ? filter : ReportExportFilter.empty();

        Field<UUID> idField = DSL.field(DSL.name("fe", "id"), UUID.class);
        Field<String> entryTypeField = DSL.field(DSL.name("fe", "entry_type"), String.class);
        Field<String> statusField = DSL.field(DSL.name("fe", "status"), String.class);
        Field<String> descriptionField = DSL.field(DSL.name("fe", "description"), String.class);
        Field<UUID> categoryIdField = DSL.field(DSL.name("fe", "category_id"), UUID.class);
        Field<UUID> accountIdField = DSL.field(DSL.name("fe", "account_id"), UUID.class);
        Field<UUID> counterpartyIdField = DSL.field(DSL.name("fe", "counterparty_id"), UUID.class);
        Field<LocalDate> competenceDateField = DSL.field(DSL.name("fe", "competence_date"), LocalDate.class);
        Field<LocalDate> dueDateField = DSL.field(DSL.name("fe", "due_date"), LocalDate.class);
        Field<LocalDate> settlementDateField = DSL.field(DSL.name("fe", "settlement_date"), LocalDate.class);
        Field<BigDecimal> originalAmountField = DSL.field(DSL.name("fe", "original_amount"), BigDecimal.class);
        Field<BigDecimal> settledAmountField = DSL.field(DSL.name("fe", "settled_amount"), BigDecimal.class);
        Field<String> currencyField = DSL.field(DSL.name("fe", "currency"), String.class);
        Field<OffsetDateTime> createdAtField = DSL.field(DSL.name("fe", "created_at"), OffsetDateTime.class);

        Field<UUID> catId = DSL.field(DSL.name("c", "id"), UUID.class);
        Field<String> categoryNameField = DSL.field(DSL.name("c", "name"), String.class);

        Field<UUID> accId = DSL.field(DSL.name("fa", "id"), UUID.class);
        Field<String> accountNameField = DSL.field(DSL.name("fa", "name"), String.class);

        Field<UUID> cpId = DSL.field(DSL.name("cp", "id"), UUID.class);
        Field<String> counterpartyNameField = DSL.field(DSL.name("cp", "legal_name"), String.class);

        Condition condition = DSL.trueCondition();

        if (effectiveFilter.entryType() != null && !effectiveFilter.entryType().isBlank()) {
            condition = condition.and(entryTypeField.eq(effectiveFilter.entryType().trim().toUpperCase(Locale.ROOT)));
        }
        if (effectiveFilter.status() != null && !effectiveFilter.status().isBlank()) {
            condition = condition.and(statusField.eq(effectiveFilter.status().trim().toUpperCase(Locale.ROOT)));
        }
        if (effectiveFilter.categoryId() != null) {
            condition = condition.and(categoryIdField.eq(effectiveFilter.categoryId()));
        }
        if (effectiveFilter.accountId() != null) {
            condition = condition.and(accountIdField.eq(effectiveFilter.accountId()));
        }
        if (effectiveFilter.dueDateFrom() != null) {
            condition = condition.and(dueDateField.ge(effectiveFilter.dueDateFrom()));
        }
        if (effectiveFilter.dueDateTo() != null) {
            condition = condition.and(dueDateField.le(effectiveFilter.dueDateTo()));
        }
        if (effectiveFilter.search() != null && !effectiveFilter.search().isBlank()) {
            String pattern = "%" + effectiveFilter.search().trim().toLowerCase(Locale.ROOT) + "%";
            condition = condition.and(
                    DSL.lower(descriptionField).like(pattern)
                            .or(DSL.lower(DSL.coalesce(counterpartyNameField, "")).like(pattern))
                            .or(DSL.lower(categoryNameField).like(pattern))
            );
        }

        return dsl.select(
                        idField,
                        entryTypeField,
                        statusField,
                        descriptionField,
                        categoryNameField,
                        accountNameField,
                        counterpartyNameField,
                        competenceDateField,
                        dueDateField,
                        settlementDateField,
                        originalAmountField,
                        settledAmountField,
                        currencyField
                )
                .from(FE)
                .innerJoin(C).on(catId.eq(categoryIdField))
                .innerJoin(FA).on(accId.eq(accountIdField))
                .leftJoin(CP).on(cpId.eq(counterpartyIdField))
                .where(condition)
                .orderBy(dueDateField.asc(), createdAtField.asc())
                .fetch(record -> new ReportRowItem(
                        record.get(idField),
                        record.get(entryTypeField),
                        record.get(statusField),
                        record.get(descriptionField),
                        record.get(categoryNameField),
                        record.get(accountNameField),
                        record.get(counterpartyNameField),
                        record.get(competenceDateField),
                        record.get(dueDateField),
                        record.get(settlementDateField),
                        record.get(originalAmountField),
                        record.get(settledAmountField),
                        record.get(currencyField)
                ));
    }

    private BigDecimal fetchActiveAccountsInitialBalance() {
        Field<BigDecimal> initialBalanceField = DSL.field(DSL.name("initial_balance"), BigDecimal.class);
        Field<Boolean> isActiveField = DSL.field(DSL.name("is_active"), Boolean.class);

        BigDecimal sum = dsl.select(DSL.coalesce(DSL.sum(initialBalanceField), BigDecimal.ZERO))
                .from(FINANCIAL_ACCOUNTS)
                .where(isActiveField.isTrue())
                .fetchOne(0, BigDecimal.class);
        return nonNull(sum);
    }

    private List<HistoricalBalancePoint> buildHistoricalSeries(LocalDate startDate, LocalDate endDate) {
        BigDecimal initialAccountsBalance = fetchActiveAccountsInitialBalance();

        Field<String> statusField = DSL.field(DSL.name("status"), String.class);
        Field<LocalDate> settlementDateField = DSL.field(DSL.name("settlement_date"), LocalDate.class);
        Field<LocalDate> dueDateField = DSL.field(DSL.name("due_date"), LocalDate.class);
        Field<LocalDate> effectiveMovementDate = DSL.coalesce(settlementDateField, dueDateField);
        Field<BigDecimal> originalAmountField = DSL.field(DSL.name("original_amount"), BigDecimal.class);
        Field<BigDecimal> settledAmountField = DSL.field(DSL.name("settled_amount"), BigDecimal.class);
        Field<BigDecimal> effectiveAmount = DSL.coalesce(settledAmountField, originalAmountField);

        Field<BigDecimal> signedAmount = DSL.when(statusField.eq("RECEIVED"), effectiveAmount)
                .when(statusField.eq("PAID"), effectiveAmount.neg())
                .otherwise(BigDecimal.ZERO);

        BigDecimal priorMovements = dsl.select(DSL.coalesce(DSL.sum(signedAmount), BigDecimal.ZERO))
                .from(FINANCIAL_ENTRIES)
                .where(statusField.in("RECEIVED", "PAID"))
                .and(effectiveMovementDate.lt(startDate))
                .fetchOne(0, BigDecimal.class);

        Field<LocalDate> movementDateAlias = effectiveMovementDate.as("movement_date");
        Field<BigDecimal> dailyNetAlias = DSL.coalesce(DSL.sum(signedAmount), BigDecimal.ZERO).as("daily_net");

        var dailyRecords = dsl.select(movementDateAlias, dailyNetAlias)
                .from(FINANCIAL_ENTRIES)
                .where(statusField.in("RECEIVED", "PAID"))
                .and(effectiveMovementDate.between(startDate, endDate))
                .groupBy(effectiveMovementDate)
                .fetch();

        Map<LocalDate, BigDecimal> dailyNetByDate = new HashMap<>();
        for (Record record : dailyRecords) {
            LocalDate date = record.get(movementDateAlias);
            BigDecimal net = record.get(dailyNetAlias);
            if (date != null && net != null) {
                dailyNetByDate.put(date, net);
            }
        }

        BigDecimal runningBalance = initialAccountsBalance.add(nonNull(priorMovements));
        List<HistoricalBalancePoint> series = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            runningBalance = runningBalance.add(dailyNetByDate.getOrDefault(date, BigDecimal.ZERO));
            series.add(new HistoricalBalancePoint(date, Money.brl(runningBalance)));
        }
        return series;
    }

    private Optional<ProjectedSimulationCone> fetchProjectedCone(
            LocalDate referenceDate,
            Optional<UUID> simulationId
    ) {
        Field<UUID> idField = DSL.field(DSL.name("id"), UUID.class);
        Field<Integer> horizonDaysField = DSL.field(DSL.name("horizon_days"), Integer.class);
        Field<Integer> numScenariosField = DSL.field(DSL.name("num_scenarios"), Integer.class);
        Field<BigDecimal> var99Field = DSL.field(DSL.name("var_99"), BigDecimal.class);
        Field<BigDecimal> medianP50Field = DSL.field(DSL.name("median_p50"), BigDecimal.class);
        Field<BigDecimal> ruinProbField = DSL.field(DSL.name("ruin_probability_pct"), BigDecimal.class);
        Field<Object> dailyBandsJsonField = DSL.field(DSL.name("daily_bands_json"));
        Field<String> executionDeviceField = DSL.field(DSL.name("execution_device"), String.class);
        Field<OffsetDateTime> createdAtField = DSL.field(DSL.name("created_at"), OffsetDateTime.class);

        Record record;
        if (simulationId != null && simulationId.isPresent()) {
            record = dsl.select(
                            idField,
                            horizonDaysField,
                            numScenariosField,
                            var99Field,
                            medianP50Field,
                            ruinProbField,
                            dailyBandsJsonField,
                            executionDeviceField,
                            createdAtField
                    )
                    .from(SIMULATION_RUNS)
                    .where(idField.eq(simulationId.get()))
                    .fetchOne();
        } else {
            record = dsl.select(
                            idField,
                            horizonDaysField,
                            numScenariosField,
                            var99Field,
                            medianP50Field,
                            ruinProbField,
                            dailyBandsJsonField,
                            executionDeviceField,
                            createdAtField
                    )
                    .from(SIMULATION_RUNS)
                    .orderBy(createdAtField.desc())
                    .limit(1)
                    .fetchOne();
        }

        if (record == null) {
            return Optional.empty();
        }

        UUID simId = record.get(idField);
        int horizonDays = Optional.ofNullable(record.get(horizonDaysField)).orElse(30);
        int numScenarios = Optional.ofNullable(record.get(numScenariosField)).orElse(100_000);
        BigDecimal var99 = nonNull(record.get(var99Field));
        BigDecimal medianP50 = nonNull(record.get(medianP50Field));
        BigDecimal ruinProb = clampPercentage(nonNull(record.get(ruinProbField)));
        String executionDevice = record.get(executionDeviceField);
        OffsetDateTime createdAtOdt = record.get(createdAtField);
        Instant createdAt = createdAtOdt != null ? createdAtOdt.toInstant() : Instant.now(clock);

        Object rawJson = record.get(dailyBandsJsonField);
        String jsonText = extractJsonString(rawJson);
        List<DailyPercentileBand> bands = parseDailyBandsJson(jsonText, referenceDate);

        return Optional.of(new ProjectedSimulationCone(
                simId,
                horizonDays,
                numScenarios,
                Money.brl(var99),
                Money.brl(medianP50),
                Percentage.of(ruinProb),
                executionDevice,
                createdAt,
                bands
        ));
    }

    static List<DailyPercentileBand> parseDailyBandsJson(String json, LocalDate referenceDate) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> objectPayloads = extractTopLevelJsonObjects(json);
        List<DailyPercentileBand> bands = new ArrayList<>(objectPayloads.size());
        int index = 1;
        for (String obj : objectPayloads) {
            int dayOffset = extractInt(obj, "dayOffset", "day_offset", index);
            LocalDate date = extractDate(obj, "date", referenceDate.plusDays(dayOffset));

            BigDecimal p50 = extractDecimal(obj, BigDecimal.ZERO, "p50", "medianP50", "p50Balance");
            BigDecimal p01 = extractDecimal(obj, p50, "p01", "var99", "var99Balance");
            BigDecimal p05 = extractDecimal(obj, p01, "p05", "p10", "p10Balance");
            BigDecimal p25 = extractDecimal(obj, p05, "p25", "p10");
            BigDecimal p75 = extractDecimal(obj, p50, "p75", "p90", "p90Balance");
            BigDecimal p95 = extractDecimal(obj, p75, "p95", "p90", "p99");

            bands.add(new DailyPercentileBand(dayOffset, date, p01, p05, p25, p50, p75, p95));
            index++;
        }
        return bands;
    }

    private static List<String> extractTopLevelJsonObjects(String json) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) {
                        start = i;
                    }
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        objects.add(json.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return objects;
    }

    private static int extractInt(String obj, String key1, String key2, int defaultValue) {
        String val = extractRawScalar(obj, key1);
        if (val == null) {
            val = extractRawScalar(obj, key2);
        }
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
                // fallback to defaultValue
            }
        }
        return defaultValue;
    }

    private static LocalDate extractDate(String obj, String key, LocalDate defaultDate) {
        String val = extractRawScalar(obj, key);
        if (val != null && !val.isBlank()) {
            try {
                return LocalDate.parse(val.trim());
            } catch (Exception ignored) {
                // fallback to defaultDate
            }
        }
        return defaultDate;
    }

    private static BigDecimal extractDecimal(String obj, BigDecimal fallback, String... keys) {
        for (String key : keys) {
            Pattern nestedAmountPattern = Pattern.compile(
                    "\"" + Pattern.quote(key) + "\"\\s*:\\s*\\{[^{}]*?\"amount\"\\s*:\\s*\"?([+-]?\\d+(?:\\.\\d+)?)\"?"
            );
            Matcher nestedMatcher = nestedAmountPattern.matcher(obj);
            if (nestedMatcher.find()) {
                return new BigDecimal(nestedMatcher.group(1));
            }

            String scalar = extractRawScalar(obj, key);
            if (scalar != null && !scalar.isBlank()) {
                try {
                    return new BigDecimal(scalar.trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return fallback;
    }

    private static String extractRawScalar(String obj, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*(?:\"([^\"]*)\"|([+-]?\\d+(?:\\.\\d+)?))"
        );
        Matcher matcher = pattern.matcher(obj);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
        return null;
    }

    private static String extractJsonString(Object rawJson) {
        if (rawJson == null) {
            return "[]";
        }
        if (rawJson instanceof JSONB jsonb) {
            return jsonb.data();
        }
        return rawJson.toString();
    }

    private static Percentage calculateDelinquencyPercentage(BigDecimal overdueAmount, BigDecimal totalValidAmount) {
        if (totalValidAmount.compareTo(BigDecimal.ZERO) <= 0 || overdueAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Percentage.zero();
        }
        BigDecimal rawPct = overdueAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(totalValidAmount, 4, RoundingMode.HALF_EVEN);
        return Percentage.of(clampPercentage(rawPct));
    }

    private static BigDecimal clampPercentage(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        return value;
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
