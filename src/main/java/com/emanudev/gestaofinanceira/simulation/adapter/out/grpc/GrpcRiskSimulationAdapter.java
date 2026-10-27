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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
    public static final int DEFAULT_MAX_CONCURRENT_SIMULATIONS = 4;
    private static final long DEFAULT_DEADLINE_MS = 5_000L;

    private final Semaphore concurrencyLimiter;
    private final boolean grpcEnabled;
    private final boolean fallbackOnUnavailable;
    private final long deadlineMs;
    private final ManagedChannel managedChannel;
    private final RiskSimulationServiceGrpc.RiskSimulationServiceBlockingStub blockingStub;

    @Autowired
    public GrpcRiskSimulationAdapter(ApplicationProperties applicationProperties) {
        this(
                applicationProperties.getSimulationGrpcHost(),
                applicationProperties.getSimulationGrpcPort(),
                applicationProperties.isSimulationGrpcEnabled(),
                applicationProperties.isSimulationGrpcFallbackEnabled(),
                applicationProperties.getSimulationGrpcDeadlineMs(),
                applicationProperties.getSimulationMaxConcurrent()
        );
    }

    public GrpcRiskSimulationAdapter(
            String host,
            int port,
            boolean grpcEnabled,
            boolean fallbackOnUnavailable,
            long deadlineMs,
            int maxConcurrentSimulations
    ) {
        this.concurrencyLimiter = new Semaphore(Math.max(1, maxConcurrentSimulations), true);
        this.grpcEnabled = grpcEnabled;
        this.fallbackOnUnavailable = fallbackOnUnavailable;
        this.deadlineMs = deadlineMs > 0 ? deadlineMs : DEFAULT_DEADLINE_MS;
        if (grpcEnabled) {
            this.managedChannel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
            this.blockingStub = RiskSimulationServiceGrpc.newBlockingStub(this.managedChannel);
        } else {
            this.managedChannel = null;
            this.blockingStub = null;
        }
    }

    public GrpcRiskSimulationAdapter(
            RiskSimulationServiceGrpc.RiskSimulationServiceBlockingStub blockingStub,
            int maxConcurrentSimulations,
            long deadlineMs,
            boolean fallbackOnUnavailable
    ) {
        this.concurrencyLimiter = new Semaphore(Math.max(1, maxConcurrentSimulations), true);
        this.grpcEnabled = true;
        this.fallbackOnUnavailable = fallbackOnUnavailable;
        this.deadlineMs = deadlineMs > 0 ? deadlineMs : DEFAULT_DEADLINE_MS;
        this.managedChannel = null;
        this.blockingStub = blockingStub;
    }

    public GrpcRiskSimulationAdapter() {
        this("localhost", 50051, false, true, DEFAULT_DEADLINE_MS, DEFAULT_MAX_CONCURRENT_SIMULATIONS);
    }

    @PreDestroy
    public void shutdown() {
        if (managedChannel != null && !managedChannel.isShutdown()) {
            managedChannel.shutdown();
        }
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
        boolean acquired = false;
        try {
            acquired = concurrencyLimiter.tryAcquire(deadlineMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new RiskEngineUnavailableException(
                        "Maximum concurrent risk simulations reached (" + DEFAULT_MAX_CONCURRENT_SIMULATIONS + "). Try again shortly."
                );
            }

            long resolvedSeed = params.resolvedSeed();
            RiskSimulationRequest request = buildProtobufRequest(initialBalance, params, resolvedSeed, scheduledItems);
            RiskSimulationResponse response = executeProtobufSimulation(request);

            return mapProtobufResponseToDomain(
                    simulationId != null ? simulationId : UUID.randomUUID(),
                    requestedBy,
                    initialBalance,
                    referenceDate != null ? referenceDate : LocalDate.now(),
                    params,
                    resolvedSeed,
                    response
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RiskEngineUnavailableException("Interrupted while waiting for HPC simulation slot", e);
        } finally {
            if (acquired) {
                concurrencyLimiter.release();
            }
        }
    }

    RiskSimulationRequest buildProtobufRequest(
            Money initialBalance,
            StressScenarioParams params,
            long resolvedSeed,
            List<ScheduledDailyCashFlow> scheduledItems
    ) {
        RiskSimulationRequest.Builder builder = RiskSimulationRequest.newBuilder()
                .setInitialBalanceCents(initialBalance.toCents())
                .setHorizonDays(params.horizonDays())
                .setNumScenarios(params.numScenarios())
                .setExpectedDefaultRate(params.expectedDefaultRate().toFraction().doubleValue())
                .setVariableCostShockPct(params.variableCostShockPct().toFraction().doubleValue())
                .setRandomSeed(resolvedSeed);

        if (scheduledItems != null) {
            for (ScheduledDailyCashFlow item : scheduledItems) {
                builder.addScheduledItems(
                        ScheduledCashFlowItem.newBuilder()
                                .setDayOffset(item.dayOffset())
                                .setPayableFixedCents(item.payableFixed().toCents())
                                .setPayableVariableCents(item.payableVariable().toCents())
                                .setReceivableCents(item.receivable().toCents())
                                .build()
                );
            }
        }
        return builder.build();
    }

    RiskSimulationResponse executeProtobufSimulation(RiskSimulationRequest request) {
        if (grpcEnabled && blockingStub != null) {
            try {
                return blockingStub
                        .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                        .simulateCashFlowRisk(request);
            } catch (StatusRuntimeException ex) {
                if (fallbackOnUnavailable) {
                    log.warn("gRPC HPC engine unreachable ({}); falling back to deterministic CPU simulation", ex.getStatus());
                    return simulateDeterministicCpu(request);
                }
                throw new RiskEngineUnavailableException(
                        "HPC Risk Simulation Engine failed with gRPC status: " + ex.getStatus(),
                        ex
                );
            }
        }
        return simulateDeterministicCpu(request);
    }

    /**
     * Deterministic parallel stochastic simulation using SplitMix64/Xorshift64*
     * matching the C++20/OpenCL kernel contract over Protobuf messages.
     */
    public RiskSimulationResponse simulateDeterministicCpu(RiskSimulationRequest request) {
        long startNanos = System.nanoTime();

        int horizon = request.getHorizonDays();
        int numScenarios = request.getNumScenarios();
        long initialBalanceCents = request.getInitialBalanceCents();
        double defaultRate = normalizeFraction(request.getExpectedDefaultRate());
        double costShock = normalizeFraction(request.getVariableCostShockPct());
        long baseSeed = request.getRandomSeed() != 0L ? request.getRandomSeed() : 42L;

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

        long[][] balancesByDay = new long[horizon][numScenarios];
        AtomicInteger ruinedScenarios = new AtomicInteger(0);

        IntStream.range(0, numScenarios).parallel().forEach(scenario -> {
            long state = mix64(baseSeed + ((long) scenario + 1L) * 0x9E3779B97F4A7C15L);
            if (state == 0L) {
                state = 0xCAFEBABE12345678L;
            }

            long currentBalance = initialBalanceCents;
            boolean ruined = currentBalance < 0L;

            for (int day = 0; day < horizon; day++) {
                long rec = receivable[day];
                long realizedRec = rec;
                if (rec > 0L && defaultRate > 0.0) {
                    state = xorshift64Star(state);
                    double uDefault = toUnitDouble(state);
                    if (uDefault < defaultRate) {
                        realizedRec = 0L;
                    }
                }

                long varPay = varPayable[day];
                long realizedVarPay = varPay;
                if (varPay > 0L && costShock > 0.0) {
                    state = xorshift64Star(state);
                    double u1 = toUnitDouble(state);
                    state = xorshift64Star(state);
                    double u2 = toUnitDouble(state);
                    // Symmetric triangular shock in [-1, 1] + upward stress drift (0.25)
                    double shockMultiplier = 1.0 + costShock * ((u1 + u2 - 1.0) + 0.25);
                    if (shockMultiplier < 0.0) {
                        shockMultiplier = 0.0;
                    }
                    realizedVarPay = Math.round(varPay * shockMultiplier);
                }

                currentBalance = currentBalance + realizedRec - fixedPayable[day] - realizedVarPay;
                if (currentBalance < 0L) {
                    ruined = true;
                }
                balancesByDay[day][scenario] = currentBalance;
            }

            if (ruined) {
                ruinedScenarios.incrementAndGet();
            }
        });

        DailyProjectionBand[] bands = new DailyProjectionBand[horizon];
        IntStream.range(0, horizon).parallel().forEach(day -> {
            long[] dayBalances = balancesByDay[day];
            Arrays.sort(dayBalances);

            long p01 = percentileSorted(dayBalances, 0.01);
            long p05 = percentileSorted(dayBalances, 0.05);
            long p25 = percentileSorted(dayBalances, 0.25);
            long p50 = percentileSorted(dayBalances, 0.50);
            long p75 = percentileSorted(dayBalances, 0.75);
            long p95 = percentileSorted(dayBalances, 0.95);

            bands[day] = DailyProjectionBand.newBuilder()
                    .setDayOffset(day + 1)
                    .setP01Cents(p01)
                    .setP05Cents(p05)
                    .setP25Cents(p25)
                    .setP50Cents(p50)
                    .setP75Cents(p75)
                    .setP95Cents(p95)
                    .build();
        });

        DailyProjectionBand terminalBand = bands[horizon - 1];
        double ruinProbabilityPct = (ruinedScenarios.get() * 100.0) / numScenarios;
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
        List<DailyPercentileBand> domainBands = new ArrayList<>(response.getDailyBandsCount());
        for (DailyProjectionBand protoBand : response.getDailyBandsList()) {
            int offset = protoBand.getDayOffset();
            domainBands.add(new DailyPercentileBand(
                    offset,
                    referenceDate.plusDays(offset),
                    Money.ofCentsBrl(protoBand.getP01Cents()),
                    Money.ofCentsBrl(protoBand.getP05Cents()),
                    Money.ofCentsBrl(protoBand.getP25Cents()),
                    Money.ofCentsBrl(protoBand.getP50Cents()),
                    Money.ofCentsBrl(protoBand.getP75Cents()),
                    Money.ofCentsBrl(protoBand.getP95Cents())
            ));
        }

        BigDecimal clampedRuinPct = BigDecimal.valueOf(response.getRuinProbabilityPct())
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_EVEN);

        return new RiskSimulationResult(
                simulationId,
                requestedBy,
                params.horizonDays(),
                params.numScenarios(),
                initialBalance,
                params.expectedDefaultRate(),
                params.variableCostShockPct(),
                resolvedSeed,
                Money.ofCentsBrl(response.getVar99Cents()),
                Money.ofCentsBrl(response.getMedianP50Cents()),
                Percentage.of(clampedRuinPct),
                domainBands,
                response.getExecutionDevice(),
                response.getExecutionTimeMs(),
                Instant.now()
        );
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
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx];
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
}
