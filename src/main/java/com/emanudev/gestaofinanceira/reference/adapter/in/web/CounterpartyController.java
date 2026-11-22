package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CounterpartyResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateCounterpartyRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.PatchCounterpartyRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.mapper.ReferenceDataWebMapper;
import com.emanudev.gestaofinanceira.reference.application.PatchCounterpartyCommand;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateCounterpartyUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetCounterpartyUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListCounterpartiesUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.UpdateCounterpartyUseCase;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class CounterpartyController implements CounterpartyControllerOpenApi {

    private final CreateCounterpartyUseCase createCounterpartyUseCase;
    private final ListCounterpartiesUseCase listCounterpartiesUseCase;
    private final GetCounterpartyUseCase getCounterpartyUseCase;
    private final UpdateCounterpartyUseCase updateCounterpartyUseCase;
    private final ReferenceDataWebMapper referenceDataWebMapper;

    @Autowired
    public CounterpartyController(
            CreateCounterpartyUseCase createCounterpartyUseCase,
            ListCounterpartiesUseCase listCounterpartiesUseCase,
            GetCounterpartyUseCase getCounterpartyUseCase,
            UpdateCounterpartyUseCase updateCounterpartyUseCase,
            ReferenceDataWebMapper referenceDataWebMapper
    ) {
        this.createCounterpartyUseCase = createCounterpartyUseCase;
        this.listCounterpartiesUseCase = listCounterpartiesUseCase;
        this.getCounterpartyUseCase = getCounterpartyUseCase;
        this.updateCounterpartyUseCase = updateCounterpartyUseCase;
        this.referenceDataWebMapper = referenceDataWebMapper;
    }

    public CounterpartyController(
            CreateCounterpartyUseCase createCounterpartyUseCase,
            ListCounterpartiesUseCase listCounterpartiesUseCase,
            GetCounterpartyUseCase getCounterpartyUseCase,
            UpdateCounterpartyUseCase updateCounterpartyUseCase
    ) {
        this(createCounterpartyUseCase, listCounterpartiesUseCase, getCounterpartyUseCase, updateCounterpartyUseCase, new ReferenceDataWebMapper());
    }

    @Override
    public ResponseEntity<PagedResult<CounterpartyResponse>> listCounterparties(int page, int size, boolean includeInactive) {
        PagedResult<Counterparty> result = listCounterpartiesUseCase.execute(PageQuery.of(page, size), includeInactive);
        return ResponseEntity.ok(PagedResult.of(
                referenceDataWebMapper.toCounterpartyResponseList(result.items()),
                result.totalElements(),
                result.page(),
                result.size()
        ));
    }

    @Override
    public ResponseEntity<CounterpartyResponse> getCounterparty(UUID id) {
        return ResponseEntity.ok(
                referenceDataWebMapper.toCounterpartyResponse(getCounterpartyUseCase.execute(id))
        );
    }

    @Override
    public ResponseEntity<CounterpartyResponse> createCounterparty(CreateCounterpartyRequest request) {
        Counterparty created = createCounterpartyUseCase.execute(
                request.legalName(),
                request.tradeName(),
                request.taxId(),
                request.counterpartyType()
        );
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(referenceDataWebMapper.toCounterpartyResponse(created));
    }

    @Override
    public ResponseEntity<CounterpartyResponse> patchCounterparty(UUID id, PatchCounterpartyRequest request) {
        Counterparty updated = updateCounterpartyUseCase.patch(id, new PatchCounterpartyCommand(request.legalName(), request.tradeName(), request.active()));
        return ResponseEntity.ok(referenceDataWebMapper.toCounterpartyResponse(updated));
    }

    @Override
    public ResponseEntity<Void> deactivateCounterparty(UUID id) {
        updateCounterpartyUseCase.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
