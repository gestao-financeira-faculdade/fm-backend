package com.emanudev.gestaofinanceira.reference.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

public record PatchCategoryRequest(
        @Size(max = 100) String name,
        Boolean requiresAttachmentAlways,
        Boolean fixedCost,
        Boolean active
) {
}
