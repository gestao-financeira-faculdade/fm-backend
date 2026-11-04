package com.emanudev.gestaofinanceira.reporting.adapter.in.web;

import com.emanudev.gestaofinanceira.reporting.application.ExecutiveKpiResponse;
import com.emanudev.gestaofinanceira.reporting.application.GetExecutiveKpisUseCase;
import com.emanudev.gestaofinanceira.reporting.application.GetUncertaintyConeUseCase;
import com.emanudev.gestaofinanceira.reporting.application.UncertaintyConeResponse;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'ANALISTA', 'AUDITOR')")
public class DashboardController implements DashboardControllerOpenApi {

    private final GetExecutiveKpisUseCase getExecutiveKpisUseCase;
    private final GetUncertaintyConeUseCase getUncertaintyConeUseCase;

    public DashboardController(
            GetExecutiveKpisUseCase getExecutiveKpisUseCase,
            GetUncertaintyConeUseCase getUncertaintyConeUseCase
    ) {
        this.getExecutiveKpisUseCase = Objects.requireNonNull(
                getExecutiveKpisUseCase,
                "getExecutiveKpisUseCase must not be null"
        );
        this.getUncertaintyConeUseCase = Objects.requireNonNull(
                getUncertaintyConeUseCase,
                "getUncertaintyConeUseCase must not be null"
        );
    }

    @Override
    public ResponseEntity<ExecutiveKpiResponse> getExecutiveKpis(LocalDate referenceDate) {
        ExecutiveKpiResponse response = referenceDate != null
                ? getExecutiveKpisUseCase.execute(referenceDate)
                : getExecutiveKpisUseCase.execute();
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UncertaintyConeResponse> getUncertaintyCone(
            Integer historyDays,
            UUID simulationId,
            LocalDate referenceDate
    ) {
        UncertaintyConeResponse response = referenceDate != null
                ? getUncertaintyConeUseCase.execute(referenceDate, historyDays, simulationId)
                : getUncertaintyConeUseCase.execute(historyDays, simulationId);
        return ResponseEntity.ok(response);
    }
}
