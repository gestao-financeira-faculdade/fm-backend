package com.emanudev.gestaofinanceira.shared.config;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.stereotype.Component;
import tools.jackson.databind.module.SimpleModule;

@Component
public class SharedDomainJacksonModule extends SimpleModule {

    public SharedDomainJacksonModule() {
        super("shared-domain-value-objects");
        setMixInAnnotation(Money.class, MoneyMixin.class);
        setMixInAnnotation(Percentage.class, PercentageMixin.class);
    }

    abstract static class MoneyMixin {

        @JsonIgnore
        abstract boolean isZero();

        @JsonIgnore
        abstract boolean isPositive();

        @JsonIgnore
        abstract boolean isNegative();
    }

    abstract static class PercentageMixin {

        @JsonIgnore
        abstract boolean isZero();

        @JsonIgnore
        abstract boolean isPositive();
    }
}
