package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.AccountBalanceQueryPort;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetAccountBalancesUseCase {

    private final AccountBalanceQueryPort accountBalanceQueryPort;
    private final Clock clock;

    public GetAccountBalancesUseCase(AccountBalanceQueryPort accountBalanceQueryPort, Clock clock) {
        this.accountBalanceQueryPort = accountBalanceQueryPort;
        this.clock = clock;
    }

    /** Initial balance plus the net of entries settled up to today, the same rule as the consolidated KPI. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> currentBalances(Collection<FinancialAccount> accounts) {
        if (accounts.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = accounts.stream().map(FinancialAccount::id).toList();
        Map<UUID, BigDecimal> net = accountBalanceQueryPort.settledNetByAccount(ids, LocalDate.now(clock));
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (FinancialAccount account : accounts) {
            balances.put(account.id(), account.initialBalance().amount().add(net.getOrDefault(account.id(), BigDecimal.ZERO)));
        }
        return balances;
    }
}
