package com.emanudev.gestaofinanceira.reporting.application.strategy.factory;

import com.emanudev.gestaofinanceira.reporting.application.ReportExportFormat;
import com.emanudev.gestaofinanceira.reporting.application.strategy.ReportExportStrategy;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class ReportExportStrategyFactory {

    private final Map<ReportExportFormat, ReportExportStrategy> strategies = new EnumMap<>(ReportExportFormat.class);

    public ReportExportStrategyFactory(Set<ReportExportStrategy> strategySet) {
        strategySet.forEach(s -> strategies.put(s.getFormat(), s));
    }

    public ReportExportStrategy getStrategy(ReportExportFormat format) {
        ReportExportStrategy strategy = strategies.get(format);
        if (strategy == null) {
            throw new BusinessRuleViolationException(
                    "INVALID_EXPORT_FORMAT",
                    "Unsupported export format: " + format
            );
        }
        return strategy;
    }
}
