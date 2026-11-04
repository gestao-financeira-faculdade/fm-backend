package com.emanudev.gestaofinanceira.entry.adapter.in.web;

import com.emanudev.gestaofinanceira.entry.adapter.in.web.mapper.FinancialEntryWebMapper;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryFilter;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntrySummaryView;
import com.emanudev.gestaofinanceira.entry.application.usecase.CancelFinancialEntryUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.CreateFinancialEntryUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.GetFinancialEntryLineageUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.GetFinancialEntryUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.ListFinancialEntriesUseCase;
import com.emanudev.gestaofinanceira.entry.application.usecase.SettleFinancialEntryUseCase;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.shared.adapter.in.web.GlobalExceptionHandler;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FinancialEntryControllerTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("0192c8a4-0000-7000-8000-000000000010");
    private static final UUID CATEGORY_ID = UUID.fromString("0192c8a4-0000-7000-8000-000000000020");
    private static final UUID COUNTERPARTY_ID = UUID.fromString("0192c8a4-0000-7000-8000-000000000030");
    private static final UUID ACTOR_ID = UUID.fromString("0192c8a4-0000-7000-8000-000000000040");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private CreateFinancialEntryUseCase createUseCase;
    @Mock
    private ListFinancialEntriesUseCase listUseCase;
    @Mock
    private GetFinancialEntryUseCase getUseCase;
    @Mock
    private SettleFinancialEntryUseCase settleUseCase;
    @Mock
    private CancelFinancialEntryUseCase cancelUseCase;
    @Mock
    private GetFinancialEntryLineageUseCase lineageUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FinancialEntryWebMapper mapper = new FinancialEntryWebMapper();
        FinancialEntryController controller = new FinancialEntryController(
                createUseCase, listUseCase, getUseCase, settleUseCase, cancelUseCase, lineageUseCase, FIXED_CLOCK, mapper
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateFinancialEntryAndReturnCreated() throws Exception {
        FinancialEntry entry = sampleEntry();
        when(createUseCase.execute(any())).thenReturn(entry);

        mockMvc.perform(post("/v1/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Fatura Cloud",
                                  "entryType": "PAYABLE",
                                  "originalAmount": 1500.00,
                                  "currency": "BRL",
                                  "dueDate": "2026-11-15",
                                  "competenceDate": "2026-10-01",
                                  "categoryId": "0192c8a4-0000-7000-8000-000000000020",
                                  "accountId": "0192c8a4-0000-7000-8000-000000000010",
                                  "counterpartyId": "0192c8a4-0000-7000-8000-000000000030"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(entry.id().toString()))
                .andExpect(jsonPath("$.description").value("Fatura Cloud"));
    }

    @Test
    void shouldListFinancialEntriesWithFilters() throws Exception {
        FinancialEntry entry = sampleEntry();
        FinancialEntrySummaryView summary = new FinancialEntrySummaryView(
                entry.id(), "idem-001", null, null, ACCOUNT_ID, "Conta Principal",
                CATEGORY_ID, "Infraestrutura", false, COUNTERPARTY_ID, "AWS",
                EntryType.PAYABLE, EntryStatus.PENDING, "Fatura Cloud", TODAY,
                TODAY.plusDays(20), null, BigDecimal.valueOf(1500.00), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "BRL", 0, 0L, FIXED_NOW, FIXED_NOW
        );
        when(listUseCase.execute(any(FinancialEntryFilter.class), any(PageQuery.class)))
                .thenReturn(PagedResult.of(List.of(summary), 1L, 0, 20));

        mockMvc.perform(get("/v1/entries")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Fatura Cloud"));
    }

    @Test
    void shouldGetFinancialEntryById() throws Exception {
        FinancialEntry entry = sampleEntry();
        when(getUseCase.getById(entry.id())).thenReturn(entry);

        mockMvc.perform(get("/v1/entries/{id}", entry.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entry.id().toString()));
    }

    @Test
    void shouldCancelFinancialEntry() throws Exception {
        FinancialEntry cancelled = sampleEntry().cancel("Serviço cancelado", ACTOR_ID, FIXED_NOW);
        when(cancelUseCase.execute(cancelled.id(), "Serviço cancelado")).thenReturn(cancelled);

        mockMvc.perform(post("/v1/entries/{id}/cancel", cancelled.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Serviço cancelado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private static FinancialEntry sampleEntry() {
        return FinancialEntry.create(
                "idem-001", ACCOUNT_ID, CATEGORY_ID, COUNTERPARTY_ID,
                EntryType.PAYABLE, "Fatura Cloud", TODAY, TODAY.plusDays(20),
                Money.brl("1500.00"), ACTOR_ID, TODAY, FIXED_NOW
        );
    }
}
