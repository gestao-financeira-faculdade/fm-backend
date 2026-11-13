package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateFinancialAccountRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.FinancialAccountResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.mapper.ReferenceDataWebMapper;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateFinancialAccountUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetFinancialAccountUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListFinancialAccountsUseCase;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FinancialAccountController implements FinancialAccountControllerOpenApi {

    private final CreateFinancialAccountUseCase createFinancialAccountUseCase;
    private final ListFinancialAccountsUseCase listFinancialAccountsUseCase;
    private final GetFinancialAccountUseCase getFinancialAccountUseCase;
    private final ReferenceDataWebMapper referenceDataWebMapper;

    @Autowired
    public FinancialAccountController(
            CreateFinancialAccountUseCase createFinancialAccountUseCase,
            ListFinancialAccountsUseCase listFinancialAccountsUseCase,
            GetFinancialAccountUseCase getFinancialAccountUseCase,
            ReferenceDataWebMapper referenceDataWebMapper
    ) {
        this.createFinancialAccountUseCase = createFinancialAccountUseCase;
        this.listFinancialAccountsUseCase = listFinancialAccountsUseCase;
        this.getFinancialAccountUseCase = getFinancialAccountUseCase;
        this.referenceDataWebMapper = referenceDataWebMapper;
    }

    public FinancialAccountController(
            CreateFinancialAccountUseCase createFinancialAccountUseCase,
            ListFinancialAccountsUseCase listFinancialAccountsUseCase,
            GetFinancialAccountUseCase getFinancialAccountUseCase
    ) {
        this(
                createFinancialAccountUseCase,
                listFinancialAccountsUseCase,
                getFinancialAccountUseCase,
                new ReferenceDataWebMapper()
        );
    }

    @Override
    public ResponseEntity<List<FinancialAccountResponse>> listAccounts() {
        return ResponseEntity.ok(
                referenceDataWebMapper.toAccountResponseList(listFinancialAccountsUseCase.execute())
        );
    }

    @Override
    public ResponseEntity<FinancialAccountResponse> getAccount(UUID id) {
        return ResponseEntity.ok(
                referenceDataWebMapper.toAccountResponse(getFinancialAccountUseCase.execute(id))
        );
    }

    @Override
    public ResponseEntity<FinancialAccountResponse> createAccount(CreateFinancialAccountRequest request) {
        FinancialAccount created = createFinancialAccountUseCase.execute(
                request.name(),
                request.accountType(),
                referenceDataWebMapper.resolveInitialBalance(request)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(referenceDataWebMapper.toAccountResponse(created));
    }
}
