package com.emanudev.gestaofinanceira.shared.application;

public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 500;

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }

    public int offset() {
        return page * size;
    }
}
