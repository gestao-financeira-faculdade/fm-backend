package com.emanudev.gestaofinanceira.simulation.adapter.in.web;

import com.emanudev.gestaofinanceira.shared.adapter.in.web.GlobalExceptionHandler;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import com.emanudev.gestaofinanceira.simulation.application.port.in.GetLatestSimulationPort;
import com.emanudev.gestaofinanceira.simulation.application.port.in.RunRiskSimulationPort;
import com.emanudev.gestaofinanceira.simulation.domain.model.DailyPercentileBand;
import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import com.emanudev.gestaofinanceira.simulation.domain.model.StressScenarioParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SimulationControllerTest {

    private static final UUID SIMULATION_ID = UUID.fromString("0192c8a4-0000-7000-8000-000000000777");

    @Mock
    private RunRiskSimulationPort runRiskSimulationPort;
    @Mock
    private GetLatestSimulationPort getLatestSimulationPort;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SimulationController(runRiskSimulationPort, getLatestSimulationPort))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRunSimulationWithRequestParametersAndReturnCreated() throws Exception {
        when(runRiskSimulationPort.runSimulation(any())).thenReturn(sampleResult());

        mockMvc.perform(post("/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonDays\":60,\"numScenarios\":200000,\"expectedDefaultRate\":8.0,\"variableCostShockPct\":15.0,\"randomSeed\":42}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SIMULATION_ID.toString()))
                .andExpect(jsonPath("$.dailyBands.length()").value(1));

        ArgumentCaptor<StressScenarioParams> captor = ArgumentCaptor.forClass(StressScenarioParams.class);
        verify(runRiskSimulationPort).runSimulation(captor.capture());
        assertThat(captor.getValue().horizonDays()).isEqualTo(60);
        assertThat(captor.getValue().numScenarios()).isEqualTo(200_000);
        assertThat(captor.getValue().randomSeed()).isEqualTo(42L);
    }

    @Test
    void shouldApplyDefaultScenarioWhenBodyIsAbsent() throws Exception {
        when(runRiskSimulationPort.runSimulation(any())).thenReturn(sampleResult());

        mockMvc.perform(post("/v1/simulations"))
                .andExpect(status().isCreated());

        ArgumentCaptor<StressScenarioParams> captor = ArgumentCaptor.forClass(StressScenarioParams.class);
        verify(runRiskSimulationPort).runSimulation(captor.capture());
        assertThat(captor.getValue().horizonDays()).isEqualTo(30);
        assertThat(captor.getValue().numScenarios()).isEqualTo(StressScenarioParams.MIN_SCENARIOS);
    }

    @Test
    void shouldRejectInvalidHorizonWithUnprocessableEntity() throws Exception {
        mockMvc.perform(post("/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonDays\":45}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void shouldReturnNotFoundWhenNoSimulationWasExecuted() throws Exception {
        when(getLatestSimulationPort.getLatestSimulation()).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/simulations/latest"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnSimulationById() throws Exception {
        when(getLatestSimulationPort.getSimulationById(SIMULATION_ID)).thenReturn(sampleResult());

        mockMvc.perform(get("/v1/simulations/{id}", SIMULATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horizonDays").value(30));
    }

    @Test
    void shouldReturnNotFoundForUnknownSimulationId() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(getLatestSimulationPort.getSimulationById(unknown))
                .thenThrow(new ResourceNotFoundException("RiskSimulationResult", unknown));

        mockMvc.perform(get("/v1/simulations/{id}", unknown))
                .andExpect(status().isNotFound());
    }

    private static RiskSimulationResult sampleResult() {
        LocalDate referenceDate = LocalDate.of(2026, 10, 26);
        DailyPercentileBand band = new DailyPercentileBand(
                1, referenceDate.plusDays(1),
                Money.brl("-500.00"), Money.brl("1000.00"), Money.brl("4000.00"),
                Money.brl("5000.00"), Money.brl("6000.00"), Money.brl("8000.00")
        );
        return new RiskSimulationResult(
                SIMULATION_ID, null, 30, 100_000, Money.brl("50000.00"),
                Percentage.of("5.00"), Percentage.of("10.00"), 42L,
                Money.brl("-500.00"), Money.brl("5000.00"), Percentage.of("2.5000"),
                List.of(band), "CPU-Fallback-SIMD-SplitMix64", 120L,
                Instant.parse("2026-10-26T12:00:00Z")
        );
    }
}
