// Parallel speedup benchmark for the CPU multi-core path of the HPC risk simulation engine.
//
// Runs the same Monte Carlo workload with 1, 2, 4, ... N worker threads and prints wall time,
// process CPU time, speedup and efficiency. It is evidence, not just a report: it exits non-zero when
//   * results differ between thread counts (parallelism must not change the answer), or
//   * the run with every hardware thread is not measurably faster than the single-thread run.
//
// Usage: speedup_benchmark [scenarios=1000000] [horizon_days=90] [repetitions=3] [min_speedup=1.5]

#include "simulation_engine.hpp"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <exception>
#include <thread>
#include <vector>

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#else
#include <sys/resource.h>
#include <sys/time.h>
#endif

using gestaofinanceira::hpc::ScheduledItem;
using gestaofinanceira::hpc::SimulationEngine;
using gestaofinanceira::hpc::SimulationOutput;
using gestaofinanceira::hpc::SimulationParams;

namespace {

// User + kernel CPU time consumed by every thread of this process, in seconds.
double process_cpu_seconds() {
#ifdef _WIN32
    FILETIME creation_time, exit_time, kernel_time, user_time;
    GetProcessTimes(GetCurrentProcess(), &creation_time, &exit_time, &kernel_time, &user_time);
    ULARGE_INTEGER kernel, user;
    kernel.LowPart = kernel_time.dwLowDateTime;
    kernel.HighPart = kernel_time.dwHighDateTime;
    user.LowPart = user_time.dwLowDateTime;
    user.HighPart = user_time.dwHighDateTime;
    return static_cast<double>(kernel.QuadPart + user.QuadPart) / 1e7;
#else
    rusage usage{};
    getrusage(RUSAGE_SELF, &usage);
    const auto seconds = [](const timeval& tv) {
        return static_cast<double>(tv.tv_sec) + static_cast<double>(tv.tv_usec) / 1e6;
    };
    return seconds(usage.ru_utime) + seconds(usage.ru_stime);
#endif
}

uint64_t checksum(const SimulationOutput& out) {
    uint64_t hash = 1469598103934665603ULL;
    const auto mix = [&hash](int64_t value) {
        hash = (hash ^ static_cast<uint64_t>(value)) * 1099511628211ULL;
    };
    mix(out.var_99_cents);
    mix(out.median_p50_cents);
    mix(static_cast<int64_t>(out.ruin_probability_pct * 1e6));
    for (const auto& band : out.daily_bands) {
        mix(band.p01_cents);
        mix(band.p05_cents);
        mix(band.p25_cents);
        mix(band.p50_cents);
        mix(band.p75_cents);
        mix(band.p95_cents);
    }
    return hash;
}

struct Measurement {
    unsigned int threads{0};
    int32_t threads_used{0};
    double wall_seconds{0.0};
    double cpu_seconds{0.0};
    uint64_t result_checksum{0};
};

} // namespace

int main(int argc, char** argv) {
    const int scenarios = argc > 1 ? std::atoi(argv[1]) : 1000000;
    const int horizon = argc > 2 ? std::atoi(argv[2]) : 90;
    const int repetitions = std::max(1, argc > 3 ? std::atoi(argv[3]) : 3);
    const double min_speedup = argc > 4 ? std::atof(argv[4]) : 1.5;

    SimulationParams params;
    params.initial_balance_cents = 5000000;
    params.horizon_days = horizon;
    params.num_scenarios = scenarios;
    params.expected_default_rate = 0.08;
    params.variable_cost_shock_pct = 0.15;
    params.random_seed = 42ULL;
    for (int32_t day = 1; day <= horizon; ++day) {
        params.scheduled_items.push_back(ScheduledItem{
            .day_offset = day,
            .payable_fixed_cents = 120000,
            .payable_variable_cents = 80000,
            .receivable_cents = 220000
        });
    }

    const unsigned int hardware_threads = std::max(1u, std::thread::hardware_concurrency());
    std::vector<unsigned int> thread_counts;
    for (unsigned int t = 1; t < hardware_threads; t *= 2) {
        thread_counts.push_back(t);
    }
    thread_counts.push_back(hardware_threads);

    std::printf("HPC engine parallel speedup benchmark\n");
    std::printf("workload: %d scenarios x %d days, best of %d run(s); hardware threads visible: %u\n\n",
                scenarios, horizon, repetitions, hardware_threads);

    std::vector<Measurement> measurements;
    try {
        for (const unsigned int threads : thread_counts) {
            const SimulationEngine engine(false, threads);
            Measurement best;
            best.threads = threads;
            best.wall_seconds = 1e300;
            for (int rep = 0; rep < repetitions; ++rep) {
                const double cpu_before = process_cpu_seconds();
                const auto wall_before = std::chrono::steady_clock::now();
                const SimulationOutput out = engine.simulate(params);
                const double wall =
                    std::chrono::duration<double>(std::chrono::steady_clock::now() - wall_before).count();
                const double cpu = process_cpu_seconds() - cpu_before;
                if (wall < best.wall_seconds) {
                    best.wall_seconds = wall;
                    best.cpu_seconds = cpu;
                }
                best.threads_used = out.threads_used;
                best.result_checksum = checksum(out);
            }
            measurements.push_back(best);
        }
    } catch (const std::exception& ex) {
        std::fprintf(stderr, "benchmark aborted: %s\n", ex.what());
        return 2;
    }

    const Measurement& baseline = measurements.front();
    bool results_match = true;

    std::printf("| threads | wall (s) | cpu (s) | cpu/wall | speedup | efficiency | same result |\n");
    std::printf("|--------:|---------:|--------:|---------:|--------:|-----------:|:-----------:|\n");
    for (const Measurement& m : measurements) {
        const double speedup = baseline.wall_seconds / m.wall_seconds;
        const bool same = m.result_checksum == baseline.result_checksum;
        results_match = results_match && same;
        std::printf("| %7d | %8.3f | %7.3f | %8.2f | %6.2fx | %9.0f%% | %11s |\n",
                    m.threads_used, m.wall_seconds, m.cpu_seconds, m.cpu_seconds / m.wall_seconds,
                    speedup, 100.0 * speedup / static_cast<double>(m.threads), same ? "yes" : "NO");
    }
    std::printf("\n");

    if (!results_match) {
        std::printf("FAIL: results differ between thread counts\n");
        return 1;
    }
    if (hardware_threads < 2) {
        std::printf("SKIP: only one hardware thread is visible, speedup cannot be measured here\n");
        return 0;
    }

    const Measurement& widest = measurements.back();
    const double final_speedup = baseline.wall_seconds / widest.wall_seconds;
    if (final_speedup < min_speedup) {
        std::printf("FAIL: speedup with %u threads is %.2fx, below the required %.2fx\n",
                    widest.threads, final_speedup, min_speedup);
        return 1;
    }

    std::printf("PASS: %.2fx faster with %u threads than with 1 (required >= %.2fx), identical results\n",
                final_speedup, widest.threads, min_speedup);
    return 0;
}
