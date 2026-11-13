#include "simulation_engine.hpp"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <numeric>
#include <stdexcept>
#include <thread>

#ifdef ENABLE_OPENCL
#define CL_TARGET_OPENCL_VERSION 120
#ifdef __APPLE__
#include <OpenCL/opencl.h>
#else
#include <CL/cl.h>
#endif
#include <fstream>
#include <sstream>
#endif

namespace gestaofinanceira::hpc {

namespace {

double normalize_fraction(double raw) noexcept {
    if (raw < 0.0) {
        return 0.0;
    }
    return raw > 1.0 ? (raw / 100.0) : raw;
}

int64_t percentile_sorted(const int64_t* sorted_data, size_t n, double quantile) noexcept {
    if (n == 0) {
        return 0;
    }
    const double pos = std::floor(quantile * static_cast<double>(n - 1));
    const size_t idx = static_cast<size_t>(std::clamp(pos, 0.0, static_cast<double>(n - 1)));
    return sorted_data[idx];
}

} // namespace

SimulationEngine::SimulationEngine(bool prefer_opencl, unsigned int num_threads)
    : prefer_opencl_(prefer_opencl),
      num_threads_(num_threads != 0 ? num_threads : std::max(1u, std::thread::hardware_concurrency())) {}

uint64_t SimulationEngine::splitmix64(uint64_t z) noexcept {
    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
    z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
    return z ^ (z >> 31);
}

uint64_t SimulationEngine::xorshift64star(uint64_t& state) noexcept {
    uint64_t x = state;
    x ^= x >> 12;
    x ^= x << 25;
    x ^= x >> 27;
    state = x;
    return x * 0x2545F4914F6CDD1DULL;
}

double SimulationEngine::to_unit_double(uint64_t bits) noexcept {
    return static_cast<double>(bits >> 11) * 0x1.0p-53;
}

SimulationOutput SimulationEngine::simulate(const SimulationParams& params) const {
    if (params.horizon_days != 30 && params.horizon_days != 60 && params.horizon_days != 90) {
        throw std::invalid_argument("horizon_days must be 30, 60, or 90");
    }
    if (params.num_scenarios < 100000 || params.num_scenarios > 1000000) {
        throw std::invalid_argument("num_scenarios must be between 100,000 and 1,000,000");
    }

    const auto start_time = std::chrono::steady_clock::now();

    const int32_t horizon = params.horizon_days;
    const int32_t scenarios = params.num_scenarios;

    std::vector<int64_t> fixed_payable(horizon, 0);
    std::vector<int64_t> var_payable(horizon, 0);
    std::vector<int64_t> receivable(horizon, 0);

    for (const auto& item : params.scheduled_items) {
        const int32_t idx = item.day_offset - 1;
        if (idx >= 0 && idx < horizon) {
            fixed_payable[idx] += item.payable_fixed_cents;
            var_payable[idx] += item.payable_variable_cents;
            receivable[idx] += item.receivable_cents;
        }
    }

    std::vector<int64_t> daily_balances(static_cast<size_t>(horizon) * static_cast<size_t>(scenarios), 0);
    std::vector<uint8_t> ruin_flags(static_cast<size_t>(scenarios), 0);
    std::string execution_device;

    bool executed_on_opencl = false;
    if (prefer_opencl_) {
        executed_on_opencl = try_execute_opencl(
            params,
            fixed_payable,
            var_payable,
            receivable,
            daily_balances,
            ruin_flags,
            execution_device
        );
    }

    unsigned int threads_used = 0;
    if (!executed_on_opencl) {
        threads_used = execute_cpu_multicore(
            params,
            fixed_payable,
            var_payable,
            receivable,
            daily_balances,
            ruin_flags,
            execution_device
        );
    }

    int64_t ruined_count = 0;
    for (uint8_t flag : ruin_flags) {
        if (flag != 0) {
            ++ruined_count;
        }
    }

    std::vector<DailyBand> bands(horizon);
    const unsigned int band_workers = std::min(static_cast<unsigned int>(horizon), num_threads_);

    std::vector<std::thread> workers;
    workers.reserve(band_workers);

    for (unsigned int w = 0; w < band_workers; ++w) {
        workers.emplace_back([&, w]() {
            for (int32_t day = static_cast<int32_t>(w); day < horizon; day += static_cast<int32_t>(band_workers)) {
                int64_t* day_ptr = daily_balances.data() + static_cast<size_t>(day) * static_cast<size_t>(scenarios);
                std::sort(day_ptr, day_ptr + scenarios);

                bands[day] = DailyBand{
                    .day_offset = day + 1,
                    .p01_cents = percentile_sorted(day_ptr, scenarios, 0.01),
                    .p05_cents = percentile_sorted(day_ptr, scenarios, 0.05),
                    .p25_cents = percentile_sorted(day_ptr, scenarios, 0.25),
                    .p50_cents = percentile_sorted(day_ptr, scenarios, 0.50),
                    .p75_cents = percentile_sorted(day_ptr, scenarios, 0.75),
                    .p95_cents = percentile_sorted(day_ptr, scenarios, 0.95)
                };
            }
        });
    }

    for (auto& t : workers) {
        t.join();
    }

    const auto elapsed_ms = std::max<int64_t>(
        1,
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - start_time
        ).count()
    );

    const DailyBand& terminal_band = bands.back();
    const double ruin_prob_pct = (static_cast<double>(ruined_count) * 100.0) / static_cast<double>(scenarios);

    return SimulationOutput{
        .var_99_cents = terminal_band.p01_cents,
        .median_p50_cents = terminal_band.p50_cents,
        .ruin_probability_pct = ruin_prob_pct,
        .execution_device = execution_device,
        .threads_used = static_cast<int32_t>(threads_used),
        .execution_time_ms = elapsed_ms,
        .daily_bands = std::move(bands)
    };
}

unsigned int SimulationEngine::execute_cpu_multicore(
    const SimulationParams& params,
    const std::vector<int64_t>& fixed_payable,
    const std::vector<int64_t>& var_payable,
    const std::vector<int64_t>& receivable,
    std::vector<int64_t>& daily_balances,
    std::vector<uint8_t>& ruin_flags,
    std::string& device_out
) const {
    const int32_t horizon = params.horizon_days;
    const int32_t scenarios = params.num_scenarios;
    const int64_t initial_balance = params.initial_balance_cents;
    const double default_rate = normalize_fraction(params.expected_default_rate);
    const double cost_shock = normalize_fraction(params.variable_cost_shock_pct);
    const uint64_t base_seed = params.random_seed != 0ULL ? params.random_seed : 42ULL;

    const unsigned int num_threads = num_threads_;

    std::vector<std::thread> threads;
    threads.reserve(num_threads);

    const int32_t chunk_size = (scenarios + static_cast<int32_t>(num_threads) - 1) / static_cast<int32_t>(num_threads);

    for (unsigned int t = 0; t < num_threads; ++t) {
        const int32_t start_idx = static_cast<int32_t>(t) * chunk_size;
        const int32_t end_idx = std::min(scenarios, start_idx + chunk_size);
        if (start_idx >= end_idx) {
            break;
        }

        threads.emplace_back([=, &fixed_payable, &var_payable, &receivable, &daily_balances, &ruin_flags]() {
            for (int32_t scenario = start_idx; scenario < end_idx; ++scenario) {
                uint64_t state = splitmix64(base_seed + static_cast<uint64_t>(scenario + 1) * 0x9E3779B97F4A7C15ULL);
                if (state == 0ULL) {
                    state = 0xCAFEBABE12345678ULL;
                }

                int64_t current_balance = initial_balance;
                uint8_t ruined = current_balance < 0 ? 1 : 0;

                for (int32_t day = 0; day < horizon; ++day) {
                    const int64_t rec = receivable[day];
                    int64_t realized_rec = rec;
                    if (rec > 0 && default_rate > 0.0) {
                        const double u_default = to_unit_double(xorshift64star(state));
                        if (u_default < default_rate) {
                            realized_rec = 0;
                        }
                    }

                    const int64_t var_pay = var_payable[day];
                    int64_t realized_var_pay = var_pay;
                    if (var_pay > 0 && cost_shock > 0.0) {
                        const double u1 = to_unit_double(xorshift64star(state));
                        const double u2 = to_unit_double(xorshift64star(state));
                        double shock_multiplier = 1.0 + cost_shock * ((u1 + u2 - 1.0) + 0.25);
                        if (shock_multiplier < 0.0) {
                            shock_multiplier = 0.0;
                        }
                        realized_var_pay = static_cast<int64_t>(std::llround(static_cast<double>(var_pay) * shock_multiplier));
                    }

                    current_balance = current_balance + realized_rec - fixed_payable[day] - realized_var_pay;
                    if (current_balance < 0) {
                        ruined = 1;
                    }
                    daily_balances[static_cast<size_t>(day) * static_cast<size_t>(scenarios) + static_cast<size_t>(scenario)] = current_balance;
                }

                ruin_flags[static_cast<size_t>(scenario)] = ruined;
            }
        });
    }

    for (auto& th : threads) {
        th.join();
    }

    // Report the threads that actually ran, not the ones that were requested.
    const unsigned int spawned = static_cast<unsigned int>(threads.size());
    device_out = "CPU-MultiCore-CPP20-" + std::to_string(spawned) + "T";
    return spawned;
}

bool SimulationEngine::try_execute_opencl(
    const SimulationParams& params,
    const std::vector<int64_t>& fixed_payable,
    const std::vector<int64_t>& var_payable,
    const std::vector<int64_t>& receivable,
    std::vector<int64_t>& daily_balances,
    std::vector<uint8_t>& ruin_flags,
    std::string& device_out
) const {
#ifndef ENABLE_OPENCL
    (void)params;
    (void)fixed_payable;
    (void)var_payable;
    (void)receivable;
    (void)daily_balances;
    (void)ruin_flags;
    (void)device_out;
    return false;
#else
    cl_uint num_platforms = 0;
    if (clGetPlatformIDs(0, nullptr, &num_platforms) != CL_SUCCESS || num_platforms == 0) {
        return false;
    }

    std::vector<cl_platform_id> platforms(num_platforms);
    if (clGetPlatformIDs(num_platforms, platforms.data(), nullptr) != CL_SUCCESS) {
        return false;
    }

    cl_device_id device_id = nullptr;
    for (cl_platform_id platform : platforms) {
        if (clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, &device_id, nullptr) == CL_SUCCESS && device_id != nullptr) {
            break;
        }
        if (clGetDeviceIDs(platform, CL_DEVICE_TYPE_CPU, 1, &device_id, nullptr) == CL_SUCCESS && device_id != nullptr) {
            break;
        }
    }
    if (device_id == nullptr) {
        return false;
    }

    std::ifstream kernel_file("kernels/monte_carlo.cl");
    if (!kernel_file.is_open()) {
        return false;
    }
    std::ostringstream ss;
    ss << kernel_file.rdbuf();
    const std::string kernel_src = ss.str();
    const char* src_ptr = kernel_src.c_str();
    const size_t src_len = kernel_src.size();

    cl_int err = CL_SUCCESS;
    cl_context context = clCreateContext(nullptr, 1, &device_id, nullptr, nullptr, &err);
    if (err != CL_SUCCESS || context == nullptr) {
        return false;
    }

    cl_command_queue queue = clCreateCommandQueue(context, device_id, 0, &err);
    if (err != CL_SUCCESS || queue == nullptr) {
        clReleaseContext(context);
        return false;
    }

    cl_program program = clCreateProgramWithSource(context, 1, &src_ptr, &src_len, &err);
    if (err != CL_SUCCESS || clBuildProgram(program, 1, &device_id, nullptr, nullptr, nullptr) != CL_SUCCESS) {
        if (program) clReleaseProgram(program);
        clReleaseCommandQueue(queue);
        clReleaseContext(context);
        return false;
    }

    cl_kernel kernel = clCreateKernel(program, "simulate_cash_flow_monte_carlo", &err);
    if (err != CL_SUCCESS || kernel == nullptr) {
        clReleaseProgram(program);
        clReleaseCommandQueue(queue);
        clReleaseContext(context);
        return false;
    }

    const size_t horizon_bytes = static_cast<size_t>(params.horizon_days) * sizeof(int64_t);
    const size_t balances_bytes = daily_balances.size() * sizeof(int64_t);
    const size_t ruin_bytes = ruin_flags.size() * sizeof(uint8_t);

    cl_int err_fixed = CL_SUCCESS;
    cl_int err_var = CL_SUCCESS;
    cl_int err_rec = CL_SUCCESS;
    cl_int err_balances = CL_SUCCESS;
    cl_int err_ruin = CL_SUCCESS;
    cl_mem buf_fixed = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, horizon_bytes, const_cast<int64_t*>(fixed_payable.data()), &err_fixed);
    cl_mem buf_var = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, horizon_bytes, const_cast<int64_t*>(var_payable.data()), &err_var);
    cl_mem buf_rec = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, horizon_bytes, const_cast<int64_t*>(receivable.data()), &err_rec);
    cl_mem buf_balances = clCreateBuffer(context, CL_MEM_WRITE_ONLY, balances_bytes, nullptr, &err_balances);
    cl_mem buf_ruin = clCreateBuffer(context, CL_MEM_WRITE_ONLY, ruin_bytes, nullptr, &err_ruin);

    const auto release_all = [&]() {
        if (buf_fixed) clReleaseMemObject(buf_fixed);
        if (buf_var) clReleaseMemObject(buf_var);
        if (buf_rec) clReleaseMemObject(buf_rec);
        if (buf_balances) clReleaseMemObject(buf_balances);
        if (buf_ruin) clReleaseMemObject(buf_ruin);
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(queue);
        clReleaseContext(context);
    };

    // A device that cannot hold the balances matrix must fall back to the CPU path, not return garbage.
    if (err_fixed != CL_SUCCESS || err_var != CL_SUCCESS || err_rec != CL_SUCCESS
        || err_balances != CL_SUCCESS || err_ruin != CL_SUCCESS) {
        release_all();
        return false;
    }

    const cl_long init_bal = params.initial_balance_cents;
    const cl_int horizon = params.horizon_days;
    const cl_int scenarios = params.num_scenarios;
    const cl_double def_rate = normalize_fraction(params.expected_default_rate);
    const cl_double shock_pct = normalize_fraction(params.variable_cost_shock_pct);
    const cl_ulong seed = params.random_seed != 0ULL ? params.random_seed : 42ULL;

    err = clSetKernelArg(kernel, 0, sizeof(cl_long), &init_bal);
    err |= clSetKernelArg(kernel, 1, sizeof(cl_int), &horizon);
    err |= clSetKernelArg(kernel, 2, sizeof(cl_int), &scenarios);
    err |= clSetKernelArg(kernel, 3, sizeof(cl_double), &def_rate);
    err |= clSetKernelArg(kernel, 4, sizeof(cl_double), &shock_pct);
    err |= clSetKernelArg(kernel, 5, sizeof(cl_ulong), &seed);
    err |= clSetKernelArg(kernel, 6, sizeof(cl_mem), &buf_fixed);
    err |= clSetKernelArg(kernel, 7, sizeof(cl_mem), &buf_var);
    err |= clSetKernelArg(kernel, 8, sizeof(cl_mem), &buf_rec);
    err |= clSetKernelArg(kernel, 9, sizeof(cl_mem), &buf_balances);
    err |= clSetKernelArg(kernel, 10, sizeof(cl_mem), &buf_ruin);

    // Every step must succeed: a partially filled output would silently corrupt the percentiles.
    const size_t global_work_size = static_cast<size_t>(params.num_scenarios);
    if (err == CL_SUCCESS) {
        err = clEnqueueNDRangeKernel(queue, kernel, 1, nullptr, &global_work_size, nullptr, 0, nullptr, nullptr);
    }
    if (err == CL_SUCCESS) {
        err = clEnqueueReadBuffer(queue, buf_balances, CL_TRUE, 0, balances_bytes, daily_balances.data(), 0, nullptr, nullptr);
    }
    if (err == CL_SUCCESS) {
        err = clEnqueueReadBuffer(queue, buf_ruin, CL_TRUE, 0, ruin_bytes, ruin_flags.data(), 0, nullptr, nullptr);
    }

    if (err == CL_SUCCESS) {
        char dev_name[128] = {0};
        clGetDeviceInfo(device_id, CL_DEVICE_NAME, sizeof(dev_name) - 1, dev_name, nullptr);
        device_out = std::string("OpenCL-") + (dev_name[0] != ' ' ? dev_name : "Device");
    } else {
        // The CPU path overwrites every cell, but start it from a clean slate anyway.
        std::fill(daily_balances.begin(), daily_balances.end(), int64_t{0});
        std::fill(ruin_flags.begin(), ruin_flags.end(), uint8_t{0});
    }

    release_all();
    return err == CL_SUCCESS;
#endif
}

} // namespace gestaofinanceira::hpc
