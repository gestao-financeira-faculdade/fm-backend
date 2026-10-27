package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateFinancialAccountRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.FinancialAccountResponse;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateFinancialAccountUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetFinancialAccountUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListFinancialAccountsUseCase;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FinancialAccountController implements FinancialAccountControllerOpenApi {

    private final CreateFinancialAccountUseCase createFinancialAccountUseCase;
    private final ListFinancialAccountsUseCase listFinancialAccountsUseCase;
    private final GetFinancialAccountUseCase getFinancialAccountUseCase;

    public FinancialAccountController(
            CreateFinancialAccountUseCase createFinancialAccountUseCase,
            ListFinancialAccountsUseCase listFinancialAccountsUseCase,
            GetFinancialAccountUseCase getFinancialAccountUseCase
    ) {
        this.createFinancialAccountUseCase = createFinancialAccountUseCase;
        this.listFinancialAccountsUseCase = listFinancialAccountsUseCase;
        this.getFinancialAccountUseCase = getFinancialAccountUseCase;
    }

    @Override
    public ResponseEntity<List<FinancialAccountResponse>> listAccounts() {
        List<FinancialAccountResponse> accounts = listFinancialAccountsUseCase.execute().stream()
                .map(FinancialAccountResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @Override
    public ResponseEntity<FinancialAccountResponse> getAccount(UUID id) {
        return ResponseEntity.ok(FinancialAccountResponse.fromDomain(getFinancialAccountUseCase.execute(id)));
    }

    @Override
    public ResponseEntity<FinancialAccountResponse> createAccount(CreateFinancialAccountRequest request) {
        String currency = Optional.ofNullable(request.currency())
                .filter(c -> !c.isBlank())
                .orElse(Money.DEFAULT_CURRENCY);
        BigDecimal balance = Optional.ofNullable(request.initialBalance()).orElse(BigDecimal.ZERO);
        FinancialAccount created = createFinancialAccountUseCase.execute(
                request.name(),
                request.accountType(),
                Money.of(balance, currency)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(FinancialAccountResponse.fromDomain(created));
    }
}
