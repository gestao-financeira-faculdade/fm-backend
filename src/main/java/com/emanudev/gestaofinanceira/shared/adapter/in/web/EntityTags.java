package com.emanudev.gestaofinanceira.shared.adapter.in.web;

import com.emanudev.gestaofinanceira.shared.domain.exception.PreconditionFailedException;

public final class EntityTags {

    private EntityTags() {
    }

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /** Accepts weak validators and lists ("W/\"3\"", "\"2\", \"3\"") and "*", as RFC 9110 allows in If-Match. */
    public static void requireMatch(String ifMatch, long currentVersion) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return;
        }
        String current = of(currentVersion);
        for (String candidate : ifMatch.split(",")) {
            String tag = candidate.trim();
            if (tag.equals("*") || tag.replaceFirst("^W/", "").equals(current)) {
                return;
            }
        }
        throw new PreconditionFailedException(
                "If-Match " + ifMatch.trim() + " does not match the current version " + current);
    }
}
