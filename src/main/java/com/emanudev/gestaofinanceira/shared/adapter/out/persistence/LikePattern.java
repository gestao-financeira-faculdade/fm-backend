package com.emanudev.gestaofinanceira.shared.adapter.out.persistence;

public final class LikePattern {

    public static final char ESCAPE = '\\';

    private LikePattern() {
    }

    public static String escape(String term) {
        return term
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
