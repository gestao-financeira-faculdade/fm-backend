package com.emanudev.gestaofinanceira.simulation.application.port.out;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.simulation.domain.model.ScheduledDailyCashFlow;

import java.time.LocalDate;
import java.util.List;

public interface CashFlowProjectionPort {

    Money calculateCurrentConsolidatedBalance();

    List<ScheduledDailyCashFlow> projectScheduledCashFlows(LocalDate referenceDate, int horizonDays);
}
