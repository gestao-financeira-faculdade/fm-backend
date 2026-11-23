CREATE TABLE simulation_runs (
    id UUID NOT NULL DEFAULT uuidv7(),
    requested_by UUID,
    horizon_days INTEGER NOT NULL,
    num_scenarios INTEGER NOT NULL,
    initial_balance NUMERIC(19, 2) NOT NULL,
    expected_default_rate NUMERIC(7, 4) NOT NULL,
    variable_cost_shock_pct NUMERIC(7, 4) NOT NULL,
    random_seed BIGINT NOT NULL,
    var_99 NUMERIC(19, 2) NOT NULL,
    median_p50 NUMERIC(19, 2) NOT NULL,
    ruin_probability_pct NUMERIC(7, 4) NOT NULL,
    daily_bands_json JSONB NOT NULL,
    execution_device VARCHAR(100) NOT NULL,
    execution_time_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_simulation_runs PRIMARY KEY (id),
    CONSTRAINT fk_simulation_runs_user FOREIGN KEY (requested_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_simulation_runs_horizon CHECK (horizon_days IN (30, 60, 90)),
    CONSTRAINT ck_simulation_runs_scenarios CHECK (num_scenarios >= 100000)
);

CREATE INDEX idx_simulation_runs_created_at ON simulation_runs (created_at DESC);
