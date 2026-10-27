package com.emanudev.gestaofinanceira.reference.domain;

public enum CategoryType {
    PAYABLE,
    RECEIVABLE,
    BOTH;

    public boolean supportsEntryType(String entryTypeName) {
        if (this == BOTH) {
            return true;
        }
        return this.name().equalsIgnoreCase(entryTypeName);
    }
}
