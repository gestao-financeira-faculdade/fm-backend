package com.emanudev.gestaofinanceira.simulation.domain.model;

import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StressScenarioParamsTest {

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 90})
    @DisplayName("Deve criar StressScenarioParams válido para horizontes de 30, 60 e 90 dias com >= 100.000 cenários")
    void shouldCreateValidParamsForAllowedHorizons(int horizonDays) {
        StressScenarioParams params = new StressScenarioParams(
                horizonDays,
                100_000,
                Percentage.of(5.0),
                Percentage.of(10.0),
                42L
        );

        assertThat(params.horizonDays()).isEqualTo(horizonDays);
        assertThat(params.numScenarios()).isEqualTo(100_000);
        assertThat(params.expectedDefaultRate()).isEqualTo(Percentage.of(5.0));
        assertThat(params.variableCostShockPct()).isEqualTo(Percentage.of(10.0));
        assertThat(params.resolvedSeed()).isEqualTo(42L);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 15, 45, 120, -30})
    @DisplayName("Deve rejeitar horizonte de simulação diferente de 30, 60 ou 90 dias")
    void shouldRejectInvalidHorizonDays(int invalidHorizon) {
        assertThatThrownBy(() -> new StressScenarioParams(
                invalidHorizon,
                100_000,
                Percentage.of(5.0),
                Percentage.of(10.0),
                42L
        ))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_SIMULATION_HORIZON");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1_000, 50_000, 99_999})
    @DisplayName("RN03.2: Deve rejeitar número de cenários inferior a 100.000")
    void shouldRejectScenariosBelowMinimum(int insufficientScenarios) {
        assertThatThrownBy(() -> new StressScenarioParams(
                30,
                insufficientScenarios,
                Percentage.of(5.0),
                Percentage.of(10.0),
                42L
        ))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("INSUFFICIENT_SIMULATION_SCENARIOS");
    }

    @Test
    @DisplayName("Deve rejeitar número de cenários superior ao limite máximo de 1.000.000")
    void shouldRejectScenariosAboveMaximum() {
        assertThatThrownBy(() -> new StressScenarioParams(
                30,
                1_000_001,
                Percentage.of(5.0),
                Percentage.of(10.0),
                42L
        ))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("EXCESSIVE_SIMULATION_SCENARIOS");
    }

    @Test
    @DisplayName("Deve rejeitar taxa de inadimplência ou choque de custo variável nulos")
    void shouldRejectNullPercentages() {
        assertThatThrownBy(() -> new StressScenarioParams(
                30,
                100_000,
                null,
                Percentage.of(10.0),
                42L
        ))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_DEFAULT_RATE");

        assertThatThrownBy(() -> new StressScenarioParams(
                30,
                100_000,
                Percentage.of(5.0),
                null,
                42L
        ))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_COST_SHOCK");
    }

    @Test
    @DisplayName("Deve gerar semente aleatória positiva quando randomSeed for nulo")
    void shouldGeneratePositiveSeedWhenNull() {
        StressScenarioParams params = new StressScenarioParams(
                30,
                100_000,
                Percentage.of(5.0),
                Percentage.of(10.0),
                null
        );

        assertThat(params.resolvedSeed()).isPositive();
    }
}
