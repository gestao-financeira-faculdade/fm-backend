package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CategoryRepositoryPort;
import com.emanudev.gestaofinanceira.reference.application.PatchCategoryCommand;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateReferenceDataUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Mock
    private CategoryRepositoryPort categoryRepository;
    @Mock
    private AuditTrailPort auditTrailPort;

    private UpdateCategoryUseCase useCase;
    private Category category;

    @BeforeEach
    void setUp() {
        useCase = new UpdateCategoryUseCase(categoryRepository, auditTrailPort, Clock.fixed(NOW, ZoneOffset.UTC));
        category = Category.create("Serviços", CategoryType.PAYABLE, false, false, NOW.minusSeconds(3600));
    }

    @Test
    void shouldPatchOnlyTheFieldsSentAndAudit() {
        when(categoryRepository.findById(category.id())).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameAndType("Serviços terceirizados", CategoryType.PAYABLE)).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Category updated = useCase.patch(category.id(), new PatchCategoryCommand("Serviços terceirizados", null, true, null));

        assertThat(updated.name()).isEqualTo("Serviços terceirizados");
        assertThat(updated.fixedCost()).isTrue();
        assertThat(updated.requiresAttachmentAlways()).isFalse();
        assertThat(updated.active()).isTrue();
        verify(auditTrailPort).recordTransition(eq("CATEGORY"), eq(category.id()), eq(AuditAction.UPDATE), eq(category), eq(updated));
    }

    @Test
    void shouldRejectRenameToExistingName() {
        Category other = Category.create("Impostos", CategoryType.PAYABLE, true, false, NOW);
        when(categoryRepository.findById(category.id())).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameAndType("Impostos", CategoryType.PAYABLE)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase.patch(category.id(), new PatchCategoryCommand("Impostos", null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("DUPLICATE_CATEGORY_NAME");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldDeactivateOnceAndBeIdempotentAfterwards() {
        when(categoryRepository.findById(category.id())).thenReturn(Optional.of(category));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.deactivate(category.id());

        verify(categoryRepository).save(org.mockito.ArgumentMatchers.argThat(saved -> !saved.active()));

        Category inactive = new Category(category.id(), category.name(), category.categoryType(), false, false, false,
                category.createdAt(), NOW);
        when(categoryRepository.findById(category.id())).thenReturn(Optional.of(inactive));
        useCase.deactivate(category.id());

        verify(categoryRepository, org.mockito.Mockito.times(1)).save(any());
    }
}
