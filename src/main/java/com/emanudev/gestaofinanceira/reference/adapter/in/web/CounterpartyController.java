package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.application.ReferenceDataService;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.reference.domain.CounterpartyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/counterparties")
public class CounterpartyController {

    private final ReferenceDataService referenceDataService;

    public CounterpartyController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping
    public ResponseEntity<List<CounterpartyResponse>> listCounterparties() {
        List<CounterpartyResponse> counterparties = referenceDataService.listCounterparties().stream()
                .map(CounterpartyResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(counterparties);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CounterpartyResponse> getCounterparty(@PathVariable UUID id) {
        return ResponseEntity.ok(CounterpartyResponse.fromDomain(referenceDataService.getCounterpartyById(id)));
    }

    @PostMapping
    public ResponseEntity<CounterpartyResponse> createCounterparty(
            @Valid @RequestBody CreateCounterpartyRequest request
    ) {
        Counterparty created = referenceDataService.createCounterparty(
                request.legalName(),
                request.tradeName(),
                request.taxId(),
                request.counterpartyType()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CounterpartyResponse.fromDomain(created));
    }

    public record CreateCounterpartyRequest(
            @NotBlank @Size(max = 180) String legalName,
            @Size(max = 180) String tradeName,
            @Size(max = 20) String taxId,
            @NotNull CounterpartyType counterpartyType
    ) {
    }

    public record CounterpartyResponse(
            UUID id,
            String legalName,
            String tradeName,
            String taxId,
            CounterpartyType counterpartyType,
            boolean active,
            Instant createdAt
    ) {
        public static CounterpartyResponse fromDomain(Counterparty counterparty) {
            return new CounterpartyResponse(
                    counterparty.id(),
                    counterparty.legalName(),
                    counterparty.tradeName(),
                    counterparty.taxId(),
                    counterparty.counterpartyType(),
                    counterparty.active(),
                    counterparty.createdAt()
            );
        }
    }
}
