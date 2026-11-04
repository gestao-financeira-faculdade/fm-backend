package com.emanudev.gestaofinanceira.simulation.domain.model;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

import java.util.Arrays;

public enum SimulationHorizon {
    DAYS_30(30),
    DAYS_60(60),
    DAYS_90(90);

    private final int days;

    SimulationHorizon(int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }

    public static SimulationHorizon fromDays(int days) {
        return Arrays.stream(values())
                .filter(h -> h.days == days)
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "INVALID_SIMULATION_HORIZON",
                        "Simulation horizon must be 30, 60 or 90 days, but was: " + days
                ));
    }

    public static boolean isValid(int days) {
        return days == 30 || days == 60 || days == 90;
    }
}
