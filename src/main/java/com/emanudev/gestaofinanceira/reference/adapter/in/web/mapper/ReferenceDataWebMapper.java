package com.emanudev.gestaofinanceira.reference.adapter.in.web.mapper;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CategoryResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CounterpartyResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateCategoryRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateCounterpartyRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateFinancialAccountRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.FinancialAccountResponse;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ReferenceDataWebMapper {

    public Money resolveInitialBalance(CreateFinancialAccountRequest request) {
        String currency = Optional.ofNullable(request.currency())
                .filter(c -> !c.isBlank())
                .orElse(Money.DEFAULT_CURRENCY);
        BigDecimal balance = Optional.ofNullable(request.initialBalance()).orElse(BigDecimal.ZERO);
        return Money.of(balance, currency);
    }

    public boolean isRequiresAttachmentAlways(CreateCategoryRequest request) {
        return Boolean.TRUE.equals(request.requiresAttachmentAlways());
    }

    public boolean isFixedCost(CreateCategoryRequest request) {
        return Boolean.TRUE.equals(request.fixedCost());
    }

    public FinancialAccountResponse toAccountResponse(FinancialAccount domain) {
        return FinancialAccountResponse.fromDomain(domain);
    }

    public List<FinancialAccountResponse> toAccountResponseList(List<FinancialAccount> domains) {
        return domains.stream().map(FinancialAccountResponse::fromDomain).toList();
    }

    public CategoryResponse toCategoryResponse(Category domain) {
        return CategoryResponse.fromDomain(domain);
    }

    public List<CategoryResponse> toCategoryResponseList(List<Category> domains) {
        return domains.stream().map(CategoryResponse::fromDomain).toList();
    }

    public CounterpartyResponse toCounterpartyResponse(Counterparty domain) {
        return CounterpartyResponse.fromDomain(domain);
    }

    public List<CounterpartyResponse> toCounterpartyResponseList(List<Counterparty> domains) {
        return domains.stream().map(CounterpartyResponse::fromDomain).toList();
    }

    public String extractLegalName(CreateCounterpartyRequest request) {
        return request.legalName();
    }
}
