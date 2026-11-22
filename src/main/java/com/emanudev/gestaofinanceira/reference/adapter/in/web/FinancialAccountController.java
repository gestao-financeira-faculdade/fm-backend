package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateFinancialAccountRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.FinancialAccountResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.PatchFinancialAccountRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.mapper.ReferenceDataWebMapper;
import com.emanudev.gestaofinanceira.reference.application.PatchFinancialAccountCommand;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateFinancialAccountUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetAccountBalancesUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetFinancialAccountUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListFinancialAccountsUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.UpdateFinancialAccountUseCase;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class FinancialAccountController implements FinancialAccountControllerOpenApi {

    private final CreateFinancialAccountUseCase createFinancialAccountUseCase;
    private final ListFinancialAccountsUseCase listFinancialAccountsUseCase;
    private final GetFinancialAccountUseCase getFinancialAccountUseCase;
    private final UpdateFinancialAccountUseCase updateFinancialAccountUseCase;
    private final GetAccountBalancesUseCase getAccountBalancesUseCase;
    private final ReferenceDataWebMapper referenceDataWebMapper;

    @Autowired
    public FinancialAccountController(
            CreateFinancialAccountUseCase createFinancialAccountUseCase,
            ListFinancialAccountsUseCase listFinancialAccountsUseCase,
            GetFinancialAccountUseCase getFinancialAccountUseCase,
            UpdateFinancialAccountUseCase updateFinancialAccountUseCase,
            GetAccountBalancesUseCase getAccountBalancesUseCase,
            ReferenceDataWebMapper referenceDataWebMapper
    ) {
        this.createFinancialAccountUseCase = createFinancialAccountUseCase;
        this.listFinancialAccountsUseCase = listFinancialAccountsUseCase;
        this.getFinancialAccountUseCase = getFinancialAccountUseCase;
        this.updateFinancialAccountUseCase = updateFinancialAccountUseCase;
        this.getAccountBalancesUseCase = getAccountBalancesUseCase;
        this.referenceDataWebMapper = referenceDataWebMapper;
    }

    public FinancialAccountController(
            CreateFinancialAccountUseCase createFinancialAccountUseCase,
            ListFinancialAccountsUseCase listFinancialAccountsUseCase,
            GetFinancialAccountUseCase getFinancialAccountUseCase,
            UpdateFinancialAccountUseCase updateFinancialAccountUseCase,
            GetAccountBalancesUseCase getAccountBalancesUseCase
    ) {
        this(createFinancialAccountUseCase, listFinancialAccountsUseCase, getFinancialAccountUseCase, updateFinancialAccountUseCase,
                getAccountBalancesUseCase, new ReferenceDataWebMapper());
    }

    @Override
    public ResponseEntity<PagedResult<FinancialAccountResponse>> listAccounts(int page, int size, boolean includeInactive) {
        PagedResult<FinancialAccount> result = listFinancialAccountsUseCase.execute(PageQuery.of(page, size), includeInactive);
        return ResponseEntity.ok(PagedResult.of(
                referenceDataWebMapper.toAccountResponseList(
                        result.items(), getAccountBalancesUseCase.currentBalances(result.items())),
                result.totalElements(),
                result.page(),
                result.size()
        ));
    }

    @Override
    public ResponseEntity<FinancialAccountResponse> getAccount(UUID id) {
        FinancialAccount account = getFinancialAccountUseCase.execute(id);
        return ResponseEntity.ok(withBalance(account));
    }

    @Override
    public ResponseEntity<FinancialAccountResponse> createAccount(CreateFinancialAccountRequest request) {
        FinancialAccount created = createFinancialAccountUseCase.execute(
                request.name(),
                request.accountType(),
                referenceDataWebMapper.resolveInitialBalance(request)
        );
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(referenceDataWebMapper.toAccountResponse(created));
    }

    @Override
    public ResponseEntity<FinancialAccountResponse> patchFinancialAccount(UUID id, PatchFinancialAccountRequest request) {
        FinancialAccount updated = updateFinancialAccountUseCase.patch(id, new PatchFinancialAccountCommand(request.name(), request.active()));
        return ResponseEntity.ok(withBalance(updated));
    }

    @Override
    public ResponseEntity<Void> deactivateFinancialAccount(UUID id) {
        updateFinancialAccountUseCase.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    private FinancialAccountResponse withBalance(FinancialAccount account) {
        return referenceDataWebMapper.toAccountResponseList(
                List.of(account), getAccountBalancesUseCase.currentBalances(List.of(account))).getFirst();
    }
}
