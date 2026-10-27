package com.emanudev.gestaofinanceira.shared.application;

import java.util.List;

public record PagedResult<T>(
        List<T> items,
        long totalElements,
        int page,
        int size,
        int totalPages
) {

    public PagedResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> PagedResult<T> of(List<T> items, long totalElements, int page, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PagedResult<>(items, totalElements, page, size, totalPages);
    }
}
