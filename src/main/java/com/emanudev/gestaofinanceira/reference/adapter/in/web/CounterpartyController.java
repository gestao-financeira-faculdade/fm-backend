package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CounterpartyResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateCounterpartyRequest;
import com.emanudev.gestaofinanceira.reference.application.ReferenceDataService;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CounterpartyController implements CounterpartyControllerOpenApi {

    private final ReferenceDataService referenceDataService;

    public CounterpartyController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @Override
    public ResponseEntity<List<CounterpartyResponse>> listCounterparties() {
        List<CounterpartyResponse> counterparties = referenceDataService.listCounterparties().stream()
                .map(CounterpartyResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(counterparties);
    }

    @Override
    public ResponseEntity<CounterpartyResponse> getCounterparty(UUID id) {
        return ResponseEntity.ok(CounterpartyResponse.fromDomain(referenceDataService.getCounterpartyById(id)));
    }

    @Override
    public ResponseEntity<CounterpartyResponse> createCounterparty(CreateCounterpartyRequest request) {
        Counterparty created = referenceDataService.createCounterparty(
                request.legalName(),
                request.tradeName(),
                request.taxId(),
                request.counterpartyType()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CounterpartyResponse.fromDomain(created));
    }
}
