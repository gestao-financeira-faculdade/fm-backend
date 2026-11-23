package com.emanudev.gestaofinanceira.reference.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface AccountBalanceQueryPort {

    Map<UUID, BigDecimal> settledNetByAccount(Collection<UUID> accountIds, LocalDate asOf);
}
