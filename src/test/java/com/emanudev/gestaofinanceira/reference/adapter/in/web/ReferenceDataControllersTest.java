package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import java.util.Map;
import java.math.BigDecimal;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetAccountBalancesUseCase;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.Mockito.verify;
import java.util.UUID;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.reference.application.usecase.UpdateFinancialAccountUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.UpdateCounterpartyUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.UpdateCategoryUseCase;
import com.emanudev.gestaofinanceira.reference.application.PatchCategoryCommand;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.mapper.ReferenceDataWebMapper;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateCategoryUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateCounterpartyUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateFinancialAccountUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetCategoryUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetCounterpartyUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetFinancialAccountUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListCategoriesUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListCounterpartiesUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListFinancialAccountsUseCase;
import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.reference.domain.CounterpartyType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.adapter.in.web.GlobalExceptionHandler;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReferenceDataControllersTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-26T12:00:00Z");

    @Mock
    private CreateFinancialAccountUseCase createAccountUseCase;
    @Mock
    private ListFinancialAccountsUseCase listAccountsUseCase;
    @Mock
    private GetFinancialAccountUseCase getAccountUseCase;
    @Mock
    private CreateCategoryUseCase createCategoryUseCase;
    @Mock
    private ListCategoriesUseCase listCategoriesUseCase;
    @Mock
    private GetCategoryUseCase getCategoryUseCase;
    @Mock
    private CreateCounterpartyUseCase createCounterpartyUseCase;
    @Mock
    private ListCounterpartiesUseCase listCounterpartiesUseCase;
    @Mock
    private GetCounterpartyUseCase getCounterpartyUseCase;
    @Mock
    private UpdateFinancialAccountUseCase updateAccountUseCase;
    @Mock
    private GetAccountBalancesUseCase accountBalancesUseCase;
    @Mock
    private UpdateCategoryUseCase updateCategoryUseCase;
    @Mock
    private UpdateCounterpartyUseCase updateCounterpartyUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReferenceDataWebMapper mapper = new ReferenceDataWebMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new FinancialAccountController(createAccountUseCase, listAccountsUseCase, getAccountUseCase, updateAccountUseCase, accountBalancesUseCase, mapper),
                        new CategoryController(createCategoryUseCase, listCategoriesUseCase, getCategoryUseCase, updateCategoryUseCase, mapper),
                        new CounterpartyController(createCounterpartyUseCase, listCounterpartiesUseCase, getCounterpartyUseCase, updateCounterpartyUseCase, mapper)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateAndListFinancialAccounts() throws Exception {
        FinancialAccount account = FinancialAccount.create("Tesouraria", AccountType.OPERATIONAL_LEDGER, Money.brl("5000.00"), FIXED_INSTANT);
        when(createAccountUseCase.execute(eq("Tesouraria"), eq(AccountType.OPERATIONAL_LEDGER), any())).thenReturn(account);
        when(listAccountsUseCase.execute(PageQuery.of(0, 50), false)).thenReturn(PagedResult.of(List.of(account), 1, 0, 50));
        when(accountBalancesUseCase.currentBalances(List.of(account))).thenReturn(Map.of(account.id(), new BigDecimal("6250.00")));

        mockMvc.perform(post("/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tesouraria\",\"accountType\":\"OPERATIONAL_LEDGER\",\"initialBalance\":5000.00}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/accounts/" + account.id()))
                .andExpect(jsonPath("$.name").value("Tesouraria"));

        mockMvc.perform(get("/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].accountType").value("OPERATIONAL_LEDGER"))
                .andExpect(jsonPath("$.items[0].initialBalance").value(5000.00))
                .andExpect(jsonPath("$.items[0].currentBalance").value(6250.00))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldCreateAndListCategories() throws Exception {
        Category category = Category.create("Impostos", CategoryType.PAYABLE, true, false, FIXED_INSTANT);
        when(createCategoryUseCase.execute("Impostos", CategoryType.PAYABLE, true, false)).thenReturn(category);
        when(listCategoriesUseCase.execute(PageQuery.of(0, 50), false)).thenReturn(PagedResult.of(List.of(category), 1, 0, 50));

        mockMvc.perform(post("/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Impostos\",\"categoryType\":\"PAYABLE\",\"requiresAttachmentAlways\":true,\"fixedCost\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiresAttachmentAlways").value(true));

        mockMvc.perform(get("/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Impostos"));
    }

    @Test
    void shouldCreateAndListCounterparties() throws Exception {
        Counterparty counterparty = Counterparty.create("AWS Brasil", "AWS", "12345678000195", CounterpartyType.SUPPLIER, FIXED_INSTANT);
        when(createCounterpartyUseCase.execute("AWS Brasil", "AWS", "12345678000195", CounterpartyType.SUPPLIER))
                .thenReturn(counterparty);
        when(listCounterpartiesUseCase.execute(PageQuery.of(0, 50), false)).thenReturn(PagedResult.of(List.of(counterparty), 1, 0, 50));

        mockMvc.perform(post("/v1/counterparties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"AWS Brasil\",\"tradeName\":\"AWS\",\"taxId\":\"12345678000195\",\"counterpartyType\":\"SUPPLIER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legalName").value("AWS Brasil"));

        mockMvc.perform(get("/v1/counterparties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].taxId").value("12345678000195"));
    }

    @Test
    void shouldListInactiveRecordsWhenRequested() throws Exception {
        when(listCategoriesUseCase.execute(PageQuery.of(1, 10), true)).thenReturn(PagedResult.of(List.of(), 11, 1, 10));

        mockMvc.perform(get("/v1/categories").param("page", "1").param("size", "10").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void shouldPatchAndDeactivateReferenceData() throws Exception {
        Category renamed = Category.create("Tributos", CategoryType.PAYABLE, true, false, FIXED_INSTANT);
        when(updateCategoryUseCase.patch(eq(renamed.id()), any())).thenReturn(renamed);

        mockMvc.perform(patch("/v1/categories/{id}", renamed.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tributos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tributos"));
        verify(updateCategoryUseCase).patch(renamed.id(), new PatchCategoryCommand("Tributos", null, null, null));

        UUID accountId = UUID.randomUUID();
        mockMvc.perform(delete("/v1/accounts/{id}", accountId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(updateAccountUseCase).deactivate(accountId);
    }

    @Test
    void shouldNotExposeFinancialAccountsAlias() throws Exception {
        mockMvc.perform(get("/v1/financial-accounts"))
                .andExpect(status().isNotFound());
    }
}
