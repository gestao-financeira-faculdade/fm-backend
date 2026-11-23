#include "simulation_engine.hpp"

#include <cmath>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

using gestaofinanceira::hpc::ScheduledItem;
using gestaofinanceira::hpc::SimulationEngine;
using gestaofinanceira::hpc::SimulationOutput;
using gestaofinanceira::hpc::SimulationParams;

namespace {

int g_failures = 0;

// Unlike <cassert>, these are NOT compiled out by -DNDEBUG, so the checks also run in Release builds.
#define CHECK(cond)                                                                        \
    do {                                                                                   \
        if (!(cond)) {                                                                     \
            ++g_failures;                                                                  \
            std::cerr << "CHECK FAILED: " #cond " (" << __FILE__ << ":" << __LINE__ << ")" \
                      << std::endl;                                                        \
        }                                                                                  \
    } while (false)

#define CHECK_EQ(actual, expected)                                                         \
    do {                                                                                   \
        const auto actual_value = (actual);                                                \
        const auto expected_value = (expected);                                            \
        if (!(actual_value == expected_value)) {                                           \
            ++g_failures;                                                                  \
            std::cerr << "CHECK_EQ FAILED: " #actual " == " #expected " -> got "           \
                      << actual_value << ", expected " << expected_value << " ("           \
                      << __FILE__ << ":" << __LINE__ << ")" << std::endl;                  \
        }                                                                                  \
    } while (false)

// Shared parity vector. The same inputs and expected values are asserted against the JVM fallback in
// GrpcRiskSimulationAdapterTest#shouldMatchCppEngineGoldenVectorOnJvmFallback. Change both together.
SimulationParams parity_params() {
    SimulationParams params;
    params.initial_balance_cents = 300000;
    params.horizon_days = 30;
    params.num_scenarios = 100000;
    params.expected_default_rate = 0.05;
    params.variable_cost_shock_pct = 0.10;
    params.random_seed = 20261026ULL;
    params.scheduled_items = {
        ScheduledItem{.day_offset = 1, .payable_fixed_cents = 150000, .payable_variable_cents = 80000, .receivable_cents = 250000},
        ScheduledItem{.day_offset = 5, .payable_fixed_cents = 300000, .payable_variable_cents = 120000, .receivable_cents = 400000},
        ScheduledItem{.day_offset = 15, .payable_fixed_cents = 200000, .payable_variable_cents = 60000, .receivable_cents = 100000},
    };
    return params;
}

void check_parity_vector(const SimulationOutput& out) {
    CHECK_EQ(out.var_99_cents, int64_t{-274219});
    CHECK_EQ(out.median_p50_cents, int64_t{132098});
    CHECK(std::abs(out.ruin_probability_pct - 9.817) < 1e-9);
    CHECK_EQ(out.daily_bands.size(), size_t{30});
    if (out.daily_bands.size() != 30) {
        return;
    }

    const auto& d1 = out.daily_bands[0];
    CHECK_EQ(d1.p01_cents, int64_t{65106});
    CHECK_EQ(d1.p05_cents, int64_t{75344});
    CHECK_EQ(d1.p25_cents, int64_t{315229});
    CHECK_EQ(d1.p50_cents, int64_t{317800});
    CHECK_EQ(d1.p75_cents, int64_t{320213});
    CHECK_EQ(d1.p95_cents, int64_t{323394});

    const auto& d5 = out.daily_bands[4];
    CHECK_EQ(d5.p01_cents, int64_t{-111068});
    CHECK_EQ(d5.p05_cents, int64_t{-91544});
    CHECK_EQ(d5.p25_cents, int64_t{289168});
    CHECK_EQ(d5.p50_cents, int64_t{294161});
    CHECK_EQ(d5.p75_cents, int64_t{298628});
    CHECK_EQ(d5.p95_cents, int64_t{304415});

    const auto& d15 = out.daily_bands[14];
    CHECK_EQ(d15.p01_cents, int64_t{-274219});
    CHECK_EQ(d15.p05_cents, int64_t{-251874});
    CHECK_EQ(d15.p25_cents, int64_t{126076});
    CHECK_EQ(d15.p50_cents, int64_t{132098});
    CHECK_EQ(d15.p75_cents, int64_t{137121});
    CHECK_EQ(d15.p95_cents, int64_t{143566});
}

void test_determinism_with_seed_42() {
    SimulationEngine engine(false);

    SimulationParams params;
    params.initial_balance_cents = 5000000; // R$ 50.000,00
    params.horizon_days = 30;
    params.num_scenarios = 100000;
    params.expected_default_rate = 0.08;
    params.variable_cost_shock_pct = 0.15;
    params.random_seed = 42ULL;

    for (int32_t day = 1; day <= 30; ++day) {
        params.scheduled_items.push_back(ScheduledItem{
            .day_offset = day,
            .payable_fixed_cents = 120000,
            .payable_variable_cents = 80000,
            .receivable_cents = 220000
        });
    }

    const SimulationOutput run1 = engine.simulate(params);
    const SimulationOutput run2 = engine.simulate(params);

    CHECK_EQ(run1.var_99_cents, run2.var_99_cents);
    CHECK_EQ(run1.median_p50_cents, run2.median_p50_cents);
    CHECK(std::abs(run1.ruin_probability_pct - run2.ruin_probability_pct) < 1e-9);
    CHECK_EQ(run1.daily_bands.size(), size_t{30});
    CHECK_EQ(run2.daily_bands.size(), size_t{30});
    if (run1.daily_bands.size() != 30 || run2.daily_bands.size() != 30) {
        return;
    }

    for (size_t i = 0; i < run1.daily_bands.size(); ++i) {
        const auto& b1 = run1.daily_bands[i];
        const auto& b2 = run2.daily_bands[i];
        CHECK_EQ(b1.day_offset, static_cast<int32_t>(i + 1));
        CHECK_EQ(b1.p01_cents, b2.p01_cents);
        CHECK_EQ(b1.p50_cents, b2.p50_cents);
        CHECK_EQ(b1.p95_cents, b2.p95_cents);

        // Monotonic percentile ordering invariant: P01 <= P05 <= P25 <= P50 <= P75 <= P95
        CHECK(b1.p01_cents <= b1.p05_cents);
        CHECK(b1.p05_cents <= b1.p25_cents);
        CHECK(b1.p25_cents <= b1.p50_cents);
        CHECK(b1.p50_cents <= b1.p75_cents);
        CHECK(b1.p75_cents <= b1.p95_cents);
    }
}

void test_ruin_probability_under_severe_stress() {
    SimulationEngine engine(false);

    SimulationParams params;
    params.initial_balance_cents = 100000; // R$ 1.000,00
    params.horizon_days = 30;
    params.num_scenarios = 100000;
    params.expected_default_rate = 0.50;
    params.variable_cost_shock_pct = 0.40;
    params.random_seed = 42ULL;

    params.scheduled_items.push_back(ScheduledItem{
        .day_offset = 1,
        .payable_fixed_cents = 150000, // R$ 1.500,00 outflow > initial balance
        .payable_variable_cents = 50000,
        .receivable_cents = 50000
    });

    const SimulationOutput result = engine.simulate(params);
    CHECK(result.ruin_probability_pct > 99.0);
    CHECK(result.var_99_cents < 0);
}

void test_validation_rejects_invalid_scenarios_and_horizon() {
    SimulationEngine engine(false);

    SimulationParams invalid_horizon;
    invalid_horizon.horizon_days = 45;
    invalid_horizon.num_scenarios = 100000;

    bool threw_horizon = false;
    try {
        (void)engine.simulate(invalid_horizon);
    } catch (const std::invalid_argument&) {
        threw_horizon = true;
    }
    CHECK(threw_horizon);

    SimulationParams invalid_scenarios;
    invalid_scenarios.horizon_days = 30;
    invalid_scenarios.num_scenarios = 50000;

    bool threw_scenarios = false;
    try {
        (void)engine.simulate(invalid_scenarios);
    } catch (const std::invalid_argument&) {
        threw_scenarios = true;
    }
    CHECK(threw_scenarios);
}

// The result must not depend on how many threads computed it: every scenario owns its PRNG stream.
void test_result_is_independent_of_thread_count() {
    const SimulationParams params = parity_params();

    for (const unsigned int threads : {1u, 2u, 3u, 8u}) {
        const SimulationOutput out = SimulationEngine(false, threads).simulate(params);
        check_parity_vector(out);
    }
}

void test_execution_device_reports_threads_actually_used() {
    const SimulationParams params = parity_params();

    const SimulationOutput single = SimulationEngine(false, 1).simulate(params);
    CHECK_EQ(single.threads_used, int32_t{1});
    CHECK_EQ(single.execution_device, std::string("CPU-MultiCore-CPP20-1T"));

    const SimulationOutput quad = SimulationEngine(false, 4).simulate(params);
    CHECK_EQ(quad.threads_used, int32_t{4});
    CHECK_EQ(quad.execution_device, std::string("CPU-MultiCore-CPP20-4T"));

    // 0 means "auto": at least one thread, and the label must agree with threads_used.
    const SimulationEngine auto_engine(false, 0);
    CHECK(auto_engine.num_threads() >= 1u);
    const SimulationOutput auto_out = auto_engine.simulate(params);
    CHECK_EQ(auto_out.threads_used, static_cast<int32_t>(auto_engine.num_threads()));
    CHECK_EQ(auto_out.execution_device,
             "CPU-MultiCore-CPP20-" + std::to_string(auto_engine.num_threads()) + "T");
}

} // namespace

int main() {
    test_determinism_with_seed_42();
    test_ruin_probability_under_severe_stress();
    test_validation_rejects_invalid_scenarios_and_horizon();
    test_result_is_independent_of_thread_count();
    test_execution_device_reports_threads_actually_used();

    if (g_failures != 0) {
        std::cerr << g_failures << " C++20 HPC simulation_engine_test check(s) FAILED" << std::endl;
        return EXIT_FAILURE;
    }
    std::cout << "All C++20 HPC simulation_engine_test checks passed!" << std::endl;
    return EXIT_SUCCESS;
}
