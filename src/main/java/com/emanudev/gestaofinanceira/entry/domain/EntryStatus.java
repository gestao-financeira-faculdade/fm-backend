package com.emanudev.gestaofinanceira.entry.domain;

public enum EntryStatus {
    PENDING,
    OVERDUE,
    PAID,
    RECEIVED,
    CANCELLED;

    public static EntryStatus terminalStatusFor(EntryType type) {
        if (type == null) {
            throw new IllegalArgumentException("EntryType must not be null");
        }
        return type == EntryType.PAYABLE ? PAID : RECEIVED;
    }

    public boolean isSettled() {
        return this == PAID || this == RECEIVED;
    }

    public boolean isTerminal() {
        return isSettled() || this == CANCELLED;
    }

    public boolean isOpen() {
        return this == PENDING || this == OVERDUE;
    }
}
