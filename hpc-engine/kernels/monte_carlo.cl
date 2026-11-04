// OpenCL 1.2+ Kernel for Stochastic Cash Flow Monte Carlo Simulation
// Parallelizes 100,000+ scenarios (1 work-item = 1 stochastic trajectory)
// Uses SplitMix64 seeding + Xoroshiro128+ / Xorshift64* PRNG in 64-bit integer arithmetic

inline ulong splitmix64(ulong z) {
    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9UL;
    z = (z ^ (z >> 27)) * 0x94D049BB133111EBUL;
    return z ^ (z >> 31);
}

inline ulong rotl64(const ulong x, int k) {
    return (x << k) | (x >> (64 - k));
}

inline ulong xoroshiro128plus_next(ulong* s0, ulong* s1) {
    const ulong v0 = *s0;
    ulong v1 = *s1;
    const ulong result = v0 + v1;

    v1 ^= v0;
    *s0 = rotl64(v0, 24) ^ v1 ^ (v1 << 16);
    *s1 = rotl64(v1, 37);

    return result;
}

inline ulong xorshift64star(ulong* state) {
    ulong x = *state;
    x ^= x >> 12;
    x ^= x << 25;
    x ^= x >> 27;
    *state = x;
    return x * 0x2545F4914F6CDD1DUL;
}

inline double to_unit_double(ulong bits) {
    return (double)(bits >> 11) * 0x1.0p-53;
}

__kernel void simulate_cash_flow_monte_carlo(
    const long initial_balance_cents,
    const int horizon_days,
    const int num_scenarios,
    const double expected_default_rate,
    const double variable_cost_shock_pct,
    const ulong random_seed,
    __global const long* payable_fixed_cents,
    __global const long* payable_variable_cents,
    __global const long* receivable_cents,
    __global long* daily_balances_out,
    __global uchar* ruin_flags_out
) {
    const int scenario_id = get_global_id(0);
    if (scenario_id >= num_scenarios) {
        return;
    }

    ulong state = splitmix64(random_seed + ((ulong)(scenario_id + 1)) * 0x9E3779B97F4A7C15UL);
    if (state == 0UL) {
        state = 0xCAFEBABE12345678UL;
    }

    long current_balance = initial_balance_cents;
    uchar ruined = (current_balance < 0L) ? 1 : 0;

    for (int day = 0; day < horizon_days; ++day) {
        const long rec = receivable_cents[day];
        long realized_rec = rec;
        if (rec > 0L && expected_default_rate > 0.0) {
            const ulong rnd = xorshift64star(&state);
            const double u_default = to_unit_double(rnd);
            if (u_default < expected_default_rate) {
                realized_rec = 0L;
            }
        }

        const long var_pay = payable_variable_cents[day];
        long realized_var_pay = var_pay;
        if (var_pay > 0L && variable_cost_shock_pct > 0.0) {
            const double u1 = to_unit_double(xorshift64star(&state));
            const double u2 = to_unit_double(xorshift64star(&state));
            double shock_multiplier = 1.0 + variable_cost_shock_pct * ((u1 + u2 - 1.0) + 0.25);
            if (shock_multiplier < 0.0) {
                shock_multiplier = 0.0;
            }
            realized_var_pay = (long)rint((double)var_pay * shock_multiplier);
        }

        current_balance = current_balance + realized_rec - payable_fixed_cents[day] - realized_var_pay;
        if (current_balance < 0L) {
            ruined = 1;
        }

        daily_balances_out[day * num_scenarios + scenario_id] = current_balance;
    }

    ruin_flags_out[scenario_id] = ruined;
}
