package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.application.ReferenceDataService;
import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FinancialAccountController implements FinancialAccountControllerOpenApi {

    private final ReferenceDataService referenceDataService;

    public FinancialAccountController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @Override
    public ResponseEntity<List<FinancialAccountResponse>> listAccounts() {
        List<FinancialAccountResponse> accounts = referenceDataService.listAccounts().stream()
                .map(FinancialAccountResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @Override
    public ResponseEntity<FinancialAccountResponse> getAccount(UUID id) {
        return ResponseEntity.ok(FinancialAccountResponse.fromDomain(referenceDataService.getAccountById(id)));
    }

    @Override
    public ResponseEntity<FinancialAccountResponse> createAccount(CreateFinancialAccountRequest request) {
        String currency = (request.currency() == null || request.currency().isBlank())
                ? Money.DEFAULT_CURRENCY
                : request.currency();
        BigDecimal balance = request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO;
        FinancialAccount created = referenceDataService.createAccount(
                request.name(),
                request.accountType(),
                Money.of(balance, currency)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(FinancialAccountResponse.fromDomain(created));
    }

    public record CreateFinancialAccountRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull AccountType accountType,
            BigDecimal initialBalance,
            @Size(min = 3, max = 3) String currency
    ) {
    }

    public record FinancialAccountResponse(
            UUID id,
            String name,
            AccountType accountType,
            BigDecimal initialBalance,
            String currency,
            boolean active,
            Instant createdAt
    ) {
        public static FinancialAccountResponse fromDomain(FinancialAccount account) {
            return new FinancialAccountResponse(
                    account.id(),
                    account.name(),
                    account.accountType(),
                    account.initialBalance().amount(),
                    account.initialBalance().currency(),
                    account.active(),
                    account.createdAt()
            );
        }
    }
}
