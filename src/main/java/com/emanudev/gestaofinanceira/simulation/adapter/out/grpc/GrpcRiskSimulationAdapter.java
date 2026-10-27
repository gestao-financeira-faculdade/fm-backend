package com.emanudev.gestaofinanceira.simulation.adapter.out.grpc;

import com.emanudev.gestaofinanceira.shared.config.ApplicationProperties;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.emanudev.gestaofinanceira.simulation.adapter.out.grpc.proto.DailyProjectionBand;
import com.emanudev.gestaofinanceira.simulation.adapter.out.grpc.proto.RiskSimulationRequest;
import com.emanudev.gestaofinanceira.simulation.adapter.out.grpc.proto.RiskSimulationResponse;
import com.emanudev.gestaofinanceira.simulation.adapter.out.grpc.proto.RiskSimulationServiceGrpc;
import com.emanudev.gestaofinanceira.simulation.adapter.out.grpc.proto.ScheduledCashFlowItem;
import com.emanudev.gestaofinanceira.simulation.application.port.out.RiskSimulationEnginePort;
import com.emanudev.gestaofinanceira.simulation.domain.exception.RiskEngineUnavailableException;
import com.emanudev.gestaofinanceira.simulation.domain.model.DailyPercentileBand;
import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import com.emanudev.gestaofinanceira.simulation.domain.model.ScheduledDailyCashFlow;
import com.emanudev.gestaofinanceira.simulation.domain.model.StressScenarioParams;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Component
public class GrpcRiskSimulationAdapter implements RiskSimulationEnginePort {

    private static final Logger log = LoggerFactory.getLogger(GrpcRiskSimulationAdapter.class);

    private final Semaphore concurrencyLimiter;
    private final int maxConcurrentSimulations;
    private final boolean grpcEnabled;
    private final boolean fallbackOnUnavailable;
    private final long deadlineMs;
    private final ManagedChannel managedChannel;
    private final RiskSimulationServiceGrpc.RiskSimulationServiceBlockingStub blockingStub;
    private final Clock clock;

    @Autowired
    public GrpcRiskSimulationAdapter(ApplicationProperties applicationProperties, Clock clock) {
        this(
                applicationProperties.getSimulationGrpcHost(),
                applicationProperties.getSimulationGrpcPort(),
                applicationProperties.isSimulationGrpcEnabled(),
                applicationProperties.isSimulationGrpcFallbackEnabled(),
                applicationProperties.getSimulationGrpcDeadlineMs(),
                applicationProperties.getSimulationMaxConcurrent(),
                clock
        );
    }

    public GrpcRiskSimulationAdapter(ApplicationProperties applicationProperties) {
        this(applicationProperties, Clock.systemUTC());
    }

    public GrpcRiskSimulationAdapter(
            String host,
            int port,
            boolean grpcEnabled,
            boolean fallbackOnUnavailable,
            long deadlineMs,
            int maxConcurrentSimulations
    ) {
        this(host, port, grpcEnabled, fallbackOnUnavailable, deadlineMs, maxConcurrentSimulations, Clock.systemUTC());
    }

    public GrpcRiskSimulationAdapter(
            String host,
            int port,
            boolean grpcEnabled,
            boolean fallbackOnUnavailable,
            long deadlineMs,
            int maxConcurrentSimulations,
            Clock clock
    ) {
        this.maxConcurrentSimulations = Math.max(1, maxConcurrentSimulations);
        this.concurrencyLimiter = new Semaphore(this.maxConcurrentSimulations, true);
        this.grpcEnabled = grpcEnabled;
        this.fallbackOnUnavailable = fallbackOnUnavailable;
        this.deadlineMs = deadlineMs > 0 ? deadlineMs : 5_000L;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.managedChannel = createManagedChannel(host, port, grpcEnabled);
        this.blockingStub = createBlockingStub(this.managedChannel);
    }

    public GrpcRiskSimulationAdapter(
            RiskSimulationServiceGrpc.RiskSimulationServiceBlockingStub blockingStub,
            int maxConcurrentSimulations,
            long deadlineMs,
            boolean fallbackOnUnavailable
    ) {
        this.maxConcurrentSimulations = Math.max(1, maxConcurrentSimulations);
        this.concurrencyLimiter = new Semaphore(this.maxConcurrentSimulations, true);
        this.grpcEnabled = true;
        this.fallbackOnUnavailable = fallbackOnUnavailable;
        this.deadlineMs = deadlineMs > 0 ? deadlineMs : 5_000L;
        this.clock = Clock.systemUTC();
        this.managedChannel = null;
        this.blockingStub = blockingStub;
    }

    public GrpcRiskSimulationAdapter() {
        this("localhost", 50051, false, true, 5_000L, 4, Clock.systemUTC());
    }

    private static ManagedChannel createManagedChannel(String host, int port, boolean enabled) {
        if (!enabled) {
            return null;
        }
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    private static RiskSimulationServiceGrpc.RiskSimulationServiceBlockingStub createBlockingStub(
            ManagedChannel channel
    ) {
        if (channel == null) {
            return null;
        }
        return RiskSimulationServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (managedChannel == null || managedChannel.isShutdown()) {
            return;
        }
        managedChannel.shutdown();
    }

    @Override
    public RiskSimulationResult simulateRisk(
            UUID simulationId,
            UUID requestedBy,
            Money initialBalance,
            LocalDate referenceDate,
            StressScenarioParams params,
            List<ScheduledDailyCashFlow> scheduledItems
    ) {
        acquireSimulationSlot();
        try {
            long resolvedSeed = params.resolvedSeed();
            RiskSimulationRequest request = buildProtobufRequest(initialBalance, params, resolvedSeed, scheduledItems);
            RiskSimulationResponse response = executeProtobufSimulation(request);
            return mapProtobufResponseToDomain(
                    simulationId != null ? simulationId : UUID.randomUUID(),
                    requestedBy,
                    initialBalance,
                    referenceDate != null ? referenceDate : LocalDate.now(clock),
                    params,
                    resolvedSeed,
                    response
            );
        } finally {
            concurrencyLimiter.release();
        }
    }

    private void acquireSimulationSlot() {
        try {
            boolean acquired = concurrencyLimiter.tryAcquire(deadlineMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new RiskEngineUnavailableException(
                        "Maximum concurrent risk simulations reached (" + maxConcurrentSimulations + "). Try again shortly."
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RiskEngineUnavailableException("Interrupted while waiting for HPC simulation slot", e);
        }
    }

    RiskSimulationRequest buildProtobufRequest(
            Money initialBalance,
            StressScenarioParams params,
            long resolvedSeed,
            List<ScheduledDailyCashFlow> scheduledItems
    ) {
        RiskSimulationRequest.Builder builder = RiskSimulationRequest.newBuilder()
                .setHorizonDays(params.horizonDays())
                .setNumScenarios(params.numScenarios())
                .setInitialBalanceCents(initialBalance.toCents())
                .setExpectedDefaultRate(params.expectedDefaultRate().toFraction().doubleValue())
                .setVariableCostShockPct(params.variableCostShockPct().toFraction().doubleValue())
                .setRandomSeed(resolvedSeed);

        if (scheduledItems == null) {
            return builder.build();
        }
        scheduledItems.stream()
                .map(this::toProtoScheduledItem)
                .forEach(builder::addScheduledItems);
        return builder.build();
    }

    private ScheduledCashFlowItem toProtoScheduledItem(ScheduledDailyCashFlow item) {
        return ScheduledCashFlowItem.newBuilder()
                .setDayOffset(item.dayOffset())
                .setPayableFixedCents(item.payableFixed().toCents())
                .setPayableVariableCents(item.payableVariable().toCents())
                .setReceivableCents(item.receivable().toCents())
                .build();
    }

    RiskSimulationResponse executeProtobufSimulation(RiskSimulationRequest request) {
        if (!grpcEnabled || blockingStub == null) {
            return simulateDeterministicCpu(request);
        }
        try {
            return blockingStub
                    .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .simulateCashFlowRisk(request);
        } catch (StatusRuntimeException ex) {
            return handleGrpcFailure(request, ex);
        }
    }

    private RiskSimulationResponse handleGrpcFailure(RiskSimulationRequest request, StatusRuntimeException ex) {
        if (!fallbackOnUnavailable) {
            throw new RiskEngineUnavailableException(
                    "HPC Risk Simulation Engine failed with gRPC status: " + ex.getStatus(),
                    ex
            );
        }
        log.atWarn()
                .addKeyValue("event.action", "simulation.grpc.fallback")
                .addKeyValue("grpc_status", ex.getStatus().getCode().name())
                .log("gRPC HPC engine unreachable; falling back to deterministic CPU simulation");
        return simulateDeterministicCpu(request);
    }

    /**
     * Deterministic parallel stochastic simulation using SplitMix64/Xorshift64*
     * matching the C++20/OpenCL kernel contract over Protobuf messages.
     */
    public RiskSimulationResponse simulateDeterministicCpu(RiskSimulationRequest request) {
        long startNanos = System.nanoTime();
        DailyCashFlowSchedule schedule = buildDailySchedule(request);
        SimulationMatrix matrix = runMonteCarloScenarios(request, schedule);
        DailyProjectionBand[] bands = computeDailyPercentileBands(matrix.balancesByDay(), request.getHorizonDays());
        return buildCpuSimulationResponse(bands, matrix.ruinedScenarios(), request.getNumScenarios(), startNanos);
    }

    private DailyCashFlowSchedule buildDailySchedule(RiskSimulationRequest request) {
        int horizon = request.getHorizonDays();
        long[] fixedPayable = new long[horizon];
        long[] varPayable = new long[horizon];
        long[] receivable = new long[horizon];

        for (ScheduledCashFlowItem item : request.getScheduledItemsList()) {
            int idx = item.getDayOffset() - 1;
            if (idx >= 0 && idx < horizon) {
                fixedPayable[idx] += item.getPayableFixedCents();
                varPayable[idx] += item.getPayableVariableCents();
                receivable[idx] += item.getReceivableCents();
            }
        }
        return new DailyCashFlowSchedule(fixedPayable, varPayable, receivable);
    }

    private SimulationMatrix runMonteCarloScenarios(RiskSimulationRequest request, DailyCashFlowSchedule schedule) {
        int horizon = request.getHorizonDays();
        int numScenarios = request.getNumScenarios();
        long baseSeed = request.getRandomSeed() != 0L ? request.getRandomSeed() : 42L;
        double defaultRate = normalizeFraction(request.getExpectedDefaultRate());
        double costShock = normalizeFraction(request.getVariableCostShockPct());

        long[][] balancesByDay = new long[horizon][numScenarios];
        AtomicInteger ruinedScenarios = new AtomicInteger(0);

        IntStream.range(0, numScenarios).parallel().forEach(scenario -> {
            boolean ruined = simulateSingleScenario(
                    scenario, baseSeed, request.getInitialBalanceCents(),
                    defaultRate, costShock, schedule, balancesByDay
            );
            if (ruined) {
                ruinedScenarios.incrementAndGet();
            }
        });
        return new SimulationMatrix(balancesByDay, ruinedScenarios.get());
    }

    private boolean simulateSingleScenario(
            int scenario,
            long baseSeed,
            long initialBalanceCents,
            double defaultRate,
            double costShock,
            DailyCashFlowSchedule schedule,
            long[][] balancesByDay
    ) {
        long state = initializePrngState(baseSeed, scenario);
        long currentBalance = initialBalanceCents;
        boolean ruined = currentBalance < 0L;

        for (int day = 0; day < balancesByDay.length; day++) {
            PrngStep receivableStep = sampleRealizedReceivable(schedule.receivable()[day], defaultRate, state);
            state = receivableStep.nextState();
            PrngStep variablePayStep = sampleRealizedVariablePayable(schedule.varPayable()[day], costShock, state);
            state = variablePayStep.nextState();

            currentBalance += receivableStep.amountCents() - schedule.fixedPayable()[day] - variablePayStep.amountCents();
            ruined = ruined || currentBalance < 0L;
            balancesByDay[day][scenario] = currentBalance;
        }
        return ruined;
    }

    private static long initializePrngState(long baseSeed, int scenario) {
        long state = mix64(baseSeed + ((long) scenario + 1L) * 0x9E3779B97F4A7C15L);
        return state != 0L ? state : 0xCAFEBABE12345678L;
    }

    private static PrngStep sampleRealizedReceivable(long receivableCents, double defaultRate, long state) {
        if (receivableCents <= 0L || defaultRate <= 0.0) {
            return new PrngStep(receivableCents, state);
        }
        long nextState = xorshift64Star(state);
        long realized = toUnitDouble(nextState) < defaultRate ? 0L : receivableCents;
        return new PrngStep(realized, nextState);
    }

    private static PrngStep sampleRealizedVariablePayable(long varPayableCents, double costShock, long state) {
        if (varPayableCents <= 0L || costShock <= 0.0) {
            return new PrngStep(varPayableCents, state);
        }
        long state1 = xorshift64Star(state);
        double u1 = toUnitDouble(state1);
        long state2 = xorshift64Star(state1);
        double u2 = toUnitDouble(state2);
        double shockMultiplier = Math.max(0.0, 1.0 + costShock * ((u1 + u2 - 1.0) + 0.25));
        return new PrngStep(Math.round(varPayableCents * shockMultiplier), state2);
    }

    private DailyProjectionBand[] computeDailyPercentileBands(long[][] balancesByDay, int horizon) {
        DailyProjectionBand[] bands = new DailyProjectionBand[horizon];
        IntStream.range(0, horizon).parallel().forEach(day ->
                bands[day] = buildDailyProjectionBand(day, balancesByDay[day])
        );
        return bands;
    }

    private DailyProjectionBand buildDailyProjectionBand(int day, long[] dayBalances) {
        Arrays.sort(dayBalances);
        return DailyProjectionBand.newBuilder()
                .setDayOffset(day + 1)
                .setP01Cents(percentileSorted(dayBalances, 0.01))
                .setP05Cents(percentileSorted(dayBalances, 0.05))
                .setP25Cents(percentileSorted(dayBalances, 0.25))
                .setP50Cents(percentileSorted(dayBalances, 0.50))
                .setP75Cents(percentileSorted(dayBalances, 0.75))
                .setP95Cents(percentileSorted(dayBalances, 0.95))
                .build();
    }

    private RiskSimulationResponse buildCpuSimulationResponse(
            DailyProjectionBand[] bands,
            int ruinedScenarios,
            int numScenarios,
            long startNanos
    ) {
        DailyProjectionBand terminalBand = bands[bands.length - 1];
        double ruinProbabilityPct = (ruinedScenarios * 100.0) / numScenarios;
        long elapsedMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));

        return RiskSimulationResponse.newBuilder()
                .setVar99Cents(terminalBand.getP01Cents())
                .setMedianP50Cents(terminalBand.getP50Cents())
                .setRuinProbabilityPct(ruinProbabilityPct)
                .setExecutionDevice("CPU-Fallback-SIMD-SplitMix64")
                .setExecutionTimeMs(elapsedMs)
                .addAllDailyBands(Arrays.asList(bands))
                .build();
    }

    private RiskSimulationResult mapProtobufResponseToDomain(
            UUID simulationId,
            UUID requestedBy,
            Money initialBalance,
            LocalDate referenceDate,
            StressScenarioParams params,
            long resolvedSeed,
            RiskSimulationResponse response
    ) {
        List<DailyPercentileBand> domainBands = response.getDailyBandsList().stream()
                .map(protoBand -> toDomainPercentileBand(referenceDate, protoBand))
                .toList();

        return new RiskSimulationResult(
                simulationId, requestedBy, params.horizonDays(), params.numScenarios(),
                initialBalance, params.expectedDefaultRate(), params.variableCostShockPct(),
                resolvedSeed, Money.ofCentsBrl(response.getVar99Cents()),
                Money.ofCentsBrl(response.getMedianP50Cents()),
                clampRuinPercentage(response.getRuinProbabilityPct()),
                domainBands, response.getExecutionDevice(),
                response.getExecutionTimeMs(), Instant.now(clock)
        );
    }

    private DailyPercentileBand toDomainPercentileBand(LocalDate referenceDate, DailyProjectionBand protoBand) {
        int offset = protoBand.getDayOffset();
        return new DailyPercentileBand(
                offset,
                referenceDate.plusDays(offset),
                Money.ofCentsBrl(protoBand.getP01Cents()),
                Money.ofCentsBrl(protoBand.getP05Cents()),
                Money.ofCentsBrl(protoBand.getP25Cents()),
                Money.ofCentsBrl(protoBand.getP50Cents()),
                Money.ofCentsBrl(protoBand.getP75Cents()),
                Money.ofCentsBrl(protoBand.getP95Cents())
        );
    }

    private static Percentage clampRuinPercentage(double rawRuinPct) {
        BigDecimal clamped = BigDecimal.valueOf(rawRuinPct)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_EVEN);
        return Percentage.of(clamped);
    }

    private static double normalizeFraction(double raw) {
        if (raw < 0.0) {
            return 0.0;
        }
        return raw > 1.0 ? raw / 100.0 : raw;
    }

    private static long percentileSorted(long[] sorted, double quantile) {
        if (sorted.length == 0) {
            return 0L;
        }
        int idx = (int) Math.floor(quantile * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long xorshift64Star(long x) {
        x ^= x >>> 12;
        x ^= x << 25;
        x ^= x >>> 27;
        return x * 0x2545F4914F6CDD1DL;
    }

    private static double toUnitDouble(long bits) {
        return ((bits >>> 11) * 0x1.0p-53);
    }

    private record DailyCashFlowSchedule(long[] fixedPayable, long[] varPayable, long[] receivable) {
    }

    private record SimulationMatrix(long[][] balancesByDay, int ruinedScenarios) {
    }

    private record PrngStep(long amountCents, long nextState) {
    }
}
