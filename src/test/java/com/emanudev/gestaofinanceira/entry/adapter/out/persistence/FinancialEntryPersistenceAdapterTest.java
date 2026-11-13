package com.emanudev.gestaofinanceira.entry.adapter.out.persistence;

import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialEntryPersistenceAdapterTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("0192c8a4-0000-7000-8000-000000000010");
    private static final UUID CATEGORY_ID = UUID.fromString("0192c8a4-0000-7000-8000-000000000020");
    private static final UUID COUNTERPARTY_ID = UUID.fromString("0192c8a4-0000-7000-8000-000000000030");
    private static final UUID ACTOR_ID = UUID.fromString("0192c8a4-0000-7000-8000-000000000040");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Mock
    private SpringDataFinancialEntryRepository repository;

    private FinancialEntryPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FinancialEntryPersistenceAdapter(repository);
    }

    @Test
    void shouldSaveAndFindFinancialEntryById() {
        FinancialEntry domain = sampleEntry();
        FinancialEntryJpaEntity entity = FinancialEntryJpaEntity.fromDomain(domain);
        when(repository.saveAndFlush(any())).thenReturn(entity);
        when(repository.findById(domain.id())).thenReturn(Optional.of(entity));

        FinancialEntry saved = adapter.save(domain);
        Optional<FinancialEntry> found = adapter.findById(domain.id());

        assertThat(saved.id()).isEqualTo(domain.id());
        assertThat(found).contains(domain);
    }

    @Test
    void shouldFindOverdueCandidatesBeforeSpecifiedDate() {
        FinancialEntry domain = sampleEntry();
        FinancialEntryJpaEntity entity = FinancialEntryJpaEntity.fromDomain(domain);
        LocalDate today = LocalDate.of(2026, 11, 20);
        when(repository.findByStatusAndDueDateBeforeOrderByDueDateAsc(EntryStatus.PENDING, today))
                .thenReturn(List.of(entity));

        List<FinancialEntry> candidates = adapter.findOverdueCandidates(today);

        assertThat(candidates).containsExactly(domain);
    }

    @Test
    void shouldCheckIfParentHasActiveChildren() {
        UUID parentId = UUID.randomUUID();
        when(repository.existsByParentEntryIdAndStatusNot(parentId, EntryStatus.CANCELLED)).thenReturn(true);

        assertThat(adapter.hasActiveChildren(parentId)).isTrue();
    }

    private static FinancialEntry sampleEntry() {
        return FinancialEntry.create(
                "idem-001", ACCOUNT_ID, CATEGORY_ID, COUNTERPARTY_ID,
                EntryType.PAYABLE, "Fatura Cloud", TODAY, TODAY.plusDays(20),
                Money.brl("1500.00"), ACTOR_ID, TODAY, FIXED_NOW
        );
    }
}
