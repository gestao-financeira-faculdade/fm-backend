#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace gestaofinanceira::hpc {

struct ScheduledItem {
    int32_t day_offset{1};
    int64_t payable_fixed_cents{0};
    int64_t payable_variable_cents{0};
    int64_t receivable_cents{0};
};

struct SimulationParams {
    int64_t initial_balance_cents{0};
    int32_t horizon_days{30};
    int32_t num_scenarios{100000};
    double expected_default_rate{0.05};
    double variable_cost_shock_pct{0.10};
    uint64_t random_seed{42ULL};
    std::vector<ScheduledItem> scheduled_items{};
};

struct DailyBand {
    int32_t day_offset{1};
    int64_t p01_cents{0};
    int64_t p05_cents{0};
    int64_t p25_cents{0};
    int64_t p50_cents{0};
    int64_t p75_cents{0};
    int64_t p95_cents{0};
};

struct SimulationOutput {
    int64_t var_99_cents{0};
    int64_t median_p50_cents{0};
    double ruin_probability_pct{0.0};
    std::string execution_device{"CPU-Fallback-SIMD-SplitMix64"};
    int64_t execution_time_ms{1};
    std::vector<DailyBand> daily_bands{};
};

class SimulationEngine {
public:
    explicit SimulationEngine(bool prefer_opencl = true);

    SimulationOutput simulate(const SimulationParams& params) const;

    static uint64_t splitmix64(uint64_t z) noexcept;
    static uint64_t xorshift64star(uint64_t& state) noexcept;
    static double to_unit_double(uint64_t bits) noexcept;

private:
    bool prefer_opencl_;

    void execute_cpu_multicore(
        const SimulationParams& params,
        const std::vector<int64_t>& fixed_payable,
        const std::vector<int64_t>& var_payable,
        const std::vector<int64_t>& receivable,
        std::vector<int64_t>& daily_balances,
        std::vector<uint8_t>& ruin_flags,
        std::string& device_out
    ) const;

    bool try_execute_opencl(
        const SimulationParams& params,
        const std::vector<int64_t>& fixed_payable,
        const std::vector<int64_t>& var_payable,
        const std::vector<int64_t>& receivable,
        std::vector<int64_t>& daily_balances,
        std::vector<uint8_t>& ruin_flags,
        std::string& device_out
    ) const;
};

} // namespace gestaofinanceira::hpc
