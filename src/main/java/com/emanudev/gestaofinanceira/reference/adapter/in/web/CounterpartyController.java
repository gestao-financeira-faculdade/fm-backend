package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CounterpartyResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateCounterpartyRequest;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateCounterpartyUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetCounterpartyUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListCounterpartiesUseCase;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CounterpartyController implements CounterpartyControllerOpenApi {

    private final CreateCounterpartyUseCase createCounterpartyUseCase;
    private final ListCounterpartiesUseCase listCounterpartiesUseCase;
    private final GetCounterpartyUseCase getCounterpartyUseCase;

    public CounterpartyController(
            CreateCounterpartyUseCase createCounterpartyUseCase,
            ListCounterpartiesUseCase listCounterpartiesUseCase,
            GetCounterpartyUseCase getCounterpartyUseCase
    ) {
        this.createCounterpartyUseCase = createCounterpartyUseCase;
        this.listCounterpartiesUseCase = listCounterpartiesUseCase;
        this.getCounterpartyUseCase = getCounterpartyUseCase;
    }

    @Override
    public ResponseEntity<List<CounterpartyResponse>> listCounterparties() {
        List<CounterpartyResponse> counterparties = listCounterpartiesUseCase.execute().stream()
                .map(CounterpartyResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(counterparties);
    }

    @Override
    public ResponseEntity<CounterpartyResponse> getCounterparty(UUID id) {
        return ResponseEntity.ok(CounterpartyResponse.fromDomain(getCounterpartyUseCase.execute(id)));
    }

    @Override
    public ResponseEntity<CounterpartyResponse> createCounterparty(CreateCounterpartyRequest request) {
        Counterparty created = createCounterpartyUseCase.execute(
                request.legalName(),
                request.tradeName(),
                request.taxId(),
                request.counterpartyType()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CounterpartyResponse.fromDomain(created));
    }
}
