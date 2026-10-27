package com.emanudev.gestaofinanceira.entry.adapter.in.scheduler;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryUseCaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OverdueTransitionScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueTransitionScheduler.class);

    private final FinancialEntryUseCaseService financialEntryUseCaseService;

    public OverdueTransitionScheduler(FinancialEntryUseCaseService financialEntryUseCaseService) {
        this.financialEntryUseCaseService = financialEntryUseCaseService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    public void runDailyMidnightOverdueSweep() {
        executeOverdueTransition("DAILY_CRON");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runStartupOverdueCatchUp() {
        executeOverdueTransition("STARTUP_CATCH_UP");
    }

    private void executeOverdueTransition(String trigger) {
        try {
            int transitionedCount = financialEntryUseCaseService.markOverdueEntries();
            log.atInfo()
                    .addKeyValue("event", "OVERDUE_SWEEP_COMPLETED")
                    .addKeyValue("trigger", trigger)
                    .addKeyValue("transitioned_count", transitionedCount)
                    .log("Completed automatic PENDING -> OVERDUE transition sweep");
        } catch (Exception ex) {
            log.atError()
                    .addKeyValue("event", "OVERDUE_SWEEP_FAILED")
                    .addKeyValue("trigger", trigger)
                    .setCause(ex)
                    .log("Failed to execute automatic PENDING -> OVERDUE transition sweep");
        }
    }
}
