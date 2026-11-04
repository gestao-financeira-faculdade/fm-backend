package com.emanudev.gestaofinanceira.reference.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull CategoryType categoryType,
        Boolean requiresAttachmentAlways,
        Boolean fixedCost
) {
}
