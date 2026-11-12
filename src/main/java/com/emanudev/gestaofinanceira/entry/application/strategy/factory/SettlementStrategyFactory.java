package com.emanudev.gestaofinanceira.entry.application.strategy.factory;

import com.emanudev.gestaofinanceira.entry.application.strategy.SettlementPersistenceStrategy;
import com.emanudev.gestaofinanceira.entry.application.strategy.SettlementType;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class SettlementStrategyFactory {

    private final Map<SettlementType, SettlementPersistenceStrategy> strategies = new EnumMap<>(SettlementType.class);

    public SettlementStrategyFactory(Set<SettlementPersistenceStrategy> strategySet) {
        strategySet.forEach(s -> strategies.put(s.getSettlementType(), s));
    }

    public SettlementPersistenceStrategy getStrategy(SettlementType settlementType) {
        SettlementPersistenceStrategy strategy = strategies.get(settlementType);
        if (strategy == null) {
            throw new BusinessRuleViolationException(
                    "INVALID_SETTLEMENT_TYPE",
                    "Unsupported settlement type: " + settlementType
            );
        }
        return strategy;
    }
}
