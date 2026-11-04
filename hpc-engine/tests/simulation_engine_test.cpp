#include "simulation_engine.hpp"

#include <cassert>
#include <cmath>
#include <iostream>
#include <stdexcept>

using gestaofinanceira::hpc::ScheduledItem;
using gestaofinanceira::hpc::SimulationEngine;
using gestaofinanceira::hpc::SimulationOutput;
using gestaofinanceira::hpc::SimulationParams;

namespace {

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

    assert(run1.var_99_cents == run2.var_99_cents);
    assert(run1.median_p50_cents == run2.median_p50_cents);
    assert(std::abs(run1.ruin_probability_pct - run2.ruin_probability_pct) < 1e-9);
    assert(run1.daily_bands.size() == 30);

    for (size_t i = 0; i < run1.daily_bands.size(); ++i) {
        const auto& b1 = run1.daily_bands[i];
        const auto& b2 = run2.daily_bands[i];
        assert(b1.day_offset == static_cast<int32_t>(i + 1));
        assert(b1.p01_cents == b2.p01_cents);
        assert(b1.p50_cents == b2.p50_cents);
        assert(b1.p95_cents == b2.p95_cents);

        // Monotonic percentile ordering invariant: P01 <= P05 <= P25 <= P50 <= P75 <= P95
        assert(b1.p01_cents <= b1.p05_cents);
        assert(b1.p05_cents <= b1.p25_cents);
        assert(b1.p25_cents <= b1.p50_cents);
        assert(b1.p50_cents <= b1.p75_cents);
        assert(b1.p75_cents <= b1.p95_cents);
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
    assert(result.ruin_probability_pct > 99.0);
    assert(result.var_99_cents < 0);
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
    assert(threw_horizon);

    SimulationParams invalid_scenarios;
    invalid_scenarios.horizon_days = 30;
    invalid_scenarios.num_scenarios = 50000;

    bool threw_scenarios = false;
    try {
        (void)engine.simulate(invalid_scenarios);
    } catch (const std::invalid_argument&) {
        threw_scenarios = true;
    }
    assert(threw_scenarios);
}

} // namespace

int main() {
    test_determinism_with_seed_42();
    test_ruin_probability_under_severe_stress();
    test_validation_rejects_invalid_scenarios_and_horizon();
    std::cout << "All C++20 HPC simulation_engine_test assertions passed!" << std::endl;
    return 0;
}
