package com.emanudev.gestaofinanceira.simulation.adapter.out.persistence;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.emanudev.gestaofinanceira.simulation.application.port.out.SimulationRunRepositoryPort;
import com.emanudev.gestaofinanceira.simulation.domain.model.DailyPercentileBand;
import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public class JooqSimulationRunRepositoryAdapter implements SimulationRunRepositoryPort {

    private static final Table<Record> SIMULATION_RUNS = DSL.table(DSL.name("simulation_runs"));

    private static final Pattern BAND_OBJ_PATTERN = Pattern.compile("\\{([^}]+)}");
    private static final Pattern FIELD_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|[-0-9.]+)");

    private final DSLContext dsl;

    public JooqSimulationRunRepositoryAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public RiskSimulationResult save(RiskSimulationResult result) {
        String bandsJson = serializeDailyBands(result.dailyBands());
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(result.createdAt(), ZoneOffset.UTC);

        dsl.insertInto(SIMULATION_RUNS)
                .set(DSL.field(DSL.name("id"), UUID.class), result.id())
                .set(DSL.field(DSL.name("requested_by"), UUID.class), result.requestedBy())
                .set(DSL.field(DSL.name("horizon_days"), Integer.class), result.horizonDays())
                .set(DSL.field(DSL.name("num_scenarios"), Integer.class), result.numScenarios())
                .set(DSL.field(DSL.name("initial_balance"), BigDecimal.class), result.initialBalance().amount())
                .set(DSL.field(DSL.name("expected_default_rate"), BigDecimal.class), result.expectedDefaultRate().value())
                .set(DSL.field(DSL.name("variable_cost_shock_pct"), BigDecimal.class), result.variableCostShockPct().value())
                .set(DSL.field(DSL.name("random_seed"), Long.class), result.randomSeed())
                .set(DSL.field(DSL.name("var_99"), BigDecimal.class), result.var99().amount())
                .set(DSL.field(DSL.name("median_p50"), BigDecimal.class), result.medianP50().amount())
                .set(DSL.field(DSL.name("ruin_probability_pct"), BigDecimal.class), result.ruinProbabilityPct().value())
                .set(DSL.field(DSL.name("daily_bands_json"), JSONB.class), JSONB.jsonb(bandsJson))
                .set(DSL.field(DSL.name("execution_device"), String.class), result.executionDevice())
                .set(DSL.field(DSL.name("execution_time_ms"), Long.class), result.executionTimeMs())
                .set(DSL.field(DSL.name("created_at"), OffsetDateTime.class), createdAt)
                .execute();

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RiskSimulationResult> findLatest() {
        Record record = dsl.selectFrom(SIMULATION_RUNS)
                .orderBy(DSL.field(DSL.name("created_at")).desc())
                .limit(1)
                .fetchOne();

        return Optional.ofNullable(record).map(this::mapRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RiskSimulationResult> findById(UUID id) {
        Record record = dsl.selectFrom(SIMULATION_RUNS)
                .where(DSL.field(DSL.name("id"), UUID.class).eq(id))
                .fetchOne();

        return Optional.ofNullable(record).map(this::mapRecord);
    }

    private RiskSimulationResult mapRecord(Record r) {
        UUID id = r.get(DSL.field(DSL.name("id"), UUID.class));
        UUID requestedBy = r.get(DSL.field(DSL.name("requested_by"), UUID.class));
        int horizonDays = r.get(DSL.field(DSL.name("horizon_days"), Integer.class));
        int numScenarios = r.get(DSL.field(DSL.name("num_scenarios"), Integer.class));
        BigDecimal initialBalance = r.get(DSL.field(DSL.name("initial_balance"), BigDecimal.class));
        BigDecimal expectedDefaultRate = r.get(DSL.field(DSL.name("expected_default_rate"), BigDecimal.class));
        BigDecimal variableCostShockPct = r.get(DSL.field(DSL.name("variable_cost_shock_pct"), BigDecimal.class));
        long randomSeed = r.get(DSL.field(DSL.name("random_seed"), Long.class));
        BigDecimal var99 = r.get(DSL.field(DSL.name("var_99"), BigDecimal.class));
        BigDecimal medianP50 = r.get(DSL.field(DSL.name("median_p50"), BigDecimal.class));
        BigDecimal ruinProb = r.get(DSL.field(DSL.name("ruin_probability_pct"), BigDecimal.class));
        JSONB bandsJsonb = r.get(DSL.field(DSL.name("daily_bands_json"), JSONB.class));
        String executionDevice = r.get(DSL.field(DSL.name("execution_device"), String.class));
        long executionTimeMs = r.get(DSL.field(DSL.name("execution_time_ms"), Long.class));
        OffsetDateTime createdAtOdt = r.get(DSL.field(DSL.name("created_at"), OffsetDateTime.class));
        Instant createdAt = createdAtOdt != null ? createdAtOdt.toInstant() : Instant.now();

        List<DailyPercentileBand> dailyBands = deserializeDailyBands(bandsJsonb != null ? bandsJsonb.data() : "[]");

        return new RiskSimulationResult(
                id,
                requestedBy,
                horizonDays,
                numScenarios,
                Money.brl(initialBalance),
                Percentage.of(expectedDefaultRate),
                Percentage.of(variableCostShockPct),
                randomSeed,
                Money.brl(var99),
                Money.brl(medianP50),
                Percentage.of(ruinProb),
                dailyBands,
                executionDevice,
                executionTimeMs,
                createdAt
        );
    }

    static String serializeDailyBands(List<DailyPercentileBand> bands) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bands.size(); i++) {
            DailyPercentileBand b = bands.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{')
                    .append("\"dayOffset\":").append(b.dayOffset()).append(',')
                    .append("\"date\":\"").append(b.date()).append("\",")
                    .append("\"p01\":").append(b.p01().amount().toPlainString()).append(',')
                    .append("\"p05\":").append(b.p05().amount().toPlainString()).append(',')
                    .append("\"p25\":").append(b.p25().amount().toPlainString()).append(',')
                    .append("\"p50\":").append(b.p50().amount().toPlainString()).append(',')
                    .append("\"p75\":").append(b.p75().amount().toPlainString()).append(',')
                    .append("\"p95\":").append(b.p95().amount().toPlainString())
                    .append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    static List<DailyPercentileBand> deserializeDailyBands(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<DailyPercentileBand> list = new ArrayList<>();
        Matcher objMatcher = BAND_OBJ_PATTERN.matcher(json);
        while (objMatcher.find()) {
            String body = objMatcher.group(1);
            int dayOffset = 1;
            LocalDate date = LocalDate.now();
            BigDecimal p01 = BigDecimal.ZERO;
            BigDecimal p05 = BigDecimal.ZERO;
            BigDecimal p25 = BigDecimal.ZERO;
            BigDecimal p50 = BigDecimal.ZERO;
            BigDecimal p75 = BigDecimal.ZERO;
            BigDecimal p95 = BigDecimal.ZERO;

            Matcher fieldMatcher = FIELD_PATTERN.matcher(body);
            while (fieldMatcher.find()) {
                String key = fieldMatcher.group(1);
                String rawVal = fieldMatcher.group(2);
                String cleanVal = rawVal.startsWith("\"") && rawVal.endsWith("\"")
                        ? rawVal.substring(1, rawVal.length() - 1)
                        : rawVal;
                switch (key) {
                    case "dayOffset" -> dayOffset = Integer.parseInt(cleanVal);
                    case "date" -> date = LocalDate.parse(cleanVal);
                    case "p01" -> p01 = new BigDecimal(cleanVal);
                    case "p05" -> p05 = new BigDecimal(cleanVal);
                    case "p25" -> p25 = new BigDecimal(cleanVal);
                    case "p50" -> p50 = new BigDecimal(cleanVal);
                    case "p75" -> p75 = new BigDecimal(cleanVal);
                    case "p95" -> p95 = new BigDecimal(cleanVal);
                    default -> {
                    }
                }
            }
            list.add(new DailyPercentileBand(
                    dayOffset,
                    date,
                    Money.brl(p01),
                    Money.brl(p05),
                    Money.brl(p25),
                    Money.brl(p50),
                    Money.brl(p75),
                    Money.brl(p95)
            ));
        }
        return list;
    }
}
