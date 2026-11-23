package com.emanudev.gestaofinanceira.reference.domain;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

public final class TaxId {

    public static final String INVALID_TAX_ID = "INVALID_TAX_ID";

    private TaxId() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    public static String normalizeAndValidate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = normalize(raw);
        if (digits == null || !raw.trim().matches("[\\d./\\-\\s]+") || !(isValidCpf(digits) || isValidCnpj(digits))) {
            throw new BusinessRuleViolationException(INVALID_TAX_ID, "taxId must be a valid CPF or CNPJ");
        }
        return digits;
    }

    static boolean isValidCpf(String digits) {
        if (digits.length() != 11 || allSame(digits)) {
            return false;
        }
        return checkDigit(digits, 9, 10) == digit(digits, 9) && checkDigit(digits, 10, 11) == digit(digits, 10);
    }

    static boolean isValidCnpj(String digits) {
        if (digits.length() != 14 || allSame(digits)) {
            return false;
        }
        int[] first = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] second = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return cnpjDigit(digits, first) == digit(digits, 12) && cnpjDigit(digits, second) == digit(digits, 13);
    }

    private static int checkDigit(String digits, int length, int startWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digit(digits, i) * (startWeight - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }

    private static int cnpjDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digit(digits, i) * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    private static int digit(String digits, int index) {
        return digits.charAt(index) - '0';
    }

    private static boolean allSame(String digits) {
        return digits.chars().allMatch(c -> c == digits.charAt(0));
    }
}
