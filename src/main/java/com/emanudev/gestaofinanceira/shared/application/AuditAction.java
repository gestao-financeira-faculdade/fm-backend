package com.emanudev.gestaofinanceira.shared.application;

public enum AuditAction {
    CREATE,
    UPDATE,
    SETTLE,
    PARTIAL_SETTLE_SPLIT,
    CANCEL_SOFT_DELETE,
    AUTO_OVERDUE_TRANSITION,
    UPLOAD_ATTACHMENT
}
