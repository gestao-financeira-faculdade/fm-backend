package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CounterpartyResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateCounterpartyRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.mapper.ReferenceDataWebMapper;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateCounterpartyUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetCounterpartyUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListCounterpartiesUseCase;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CounterpartyController implements CounterpartyControllerOpenApi {

    private final CreateCounterpartyUseCase createCounterpartyUseCase;
    private final ListCounterpartiesUseCase listCounterpartiesUseCase;
    private final GetCounterpartyUseCase getCounterpartyUseCase;
    private final ReferenceDataWebMapper referenceDataWebMapper;

    @Autowired
    public CounterpartyController(
            CreateCounterpartyUseCase createCounterpartyUseCase,
            ListCounterpartiesUseCase listCounterpartiesUseCase,
            GetCounterpartyUseCase getCounterpartyUseCase,
            ReferenceDataWebMapper referenceDataWebMapper
    ) {
        this.createCounterpartyUseCase = createCounterpartyUseCase;
        this.listCounterpartiesUseCase = listCounterpartiesUseCase;
        this.getCounterpartyUseCase = getCounterpartyUseCase;
        this.referenceDataWebMapper = referenceDataWebMapper;
    }

    public CounterpartyController(
            CreateCounterpartyUseCase createCounterpartyUseCase,
            ListCounterpartiesUseCase listCounterpartiesUseCase,
            GetCounterpartyUseCase getCounterpartyUseCase
    ) {
        this(
                createCounterpartyUseCase,
                listCounterpartiesUseCase,
                getCounterpartyUseCase,
                new ReferenceDataWebMapper()
        );
    }

    @Override
    public ResponseEntity<List<CounterpartyResponse>> listCounterparties() {
        return ResponseEntity.ok(
                referenceDataWebMapper.toCounterpartyResponseList(listCounterpartiesUseCase.execute())
        );
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(referenceDataWebMapper.toCounterpartyResponse(created));
    }
}
