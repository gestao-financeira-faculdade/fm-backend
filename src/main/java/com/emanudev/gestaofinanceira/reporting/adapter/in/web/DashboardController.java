package com.emanudev.gestaofinanceira.reporting.adapter.in.web;

import com.emanudev.gestaofinanceira.reporting.application.ExecutiveKpiResponse;
import com.emanudev.gestaofinanceira.reporting.application.GetExecutiveKpisUseCase;
import com.emanudev.gestaofinanceira.reporting.application.GetUncertaintyConeUseCase;
import com.emanudev.gestaofinanceira.reporting.application.UncertaintyConeResponse;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'ANALISTA', 'AUDITOR')")
public class DashboardController {

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

    @GetMapping("/kpis")
    public ResponseEntity<ExecutiveKpiResponse> getExecutiveKpis(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate referenceDate
    ) {
        ExecutiveKpiResponse response = referenceDate != null
                ? getExecutiveKpisUseCase.execute(referenceDate)
                : getExecutiveKpisUseCase.execute();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/uncertainty-cone")
    public ResponseEntity<UncertaintyConeResponse> getUncertaintyCone(
            @RequestParam(required = false) Integer historyDays,
            @RequestParam(required = false) UUID simulationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate referenceDate
    ) {
        UncertaintyConeResponse response = referenceDate != null
                ? getUncertaintyConeUseCase.execute(referenceDate, historyDays, simulationId)
                : getUncertaintyConeUseCase.execute(historyDays, simulationId);
        return ResponseEntity.ok(response);
    }
}
