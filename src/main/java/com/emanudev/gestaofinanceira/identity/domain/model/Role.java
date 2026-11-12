package com.emanudev.gestaofinanceira.identity.domain.model;

public enum Role {
    ADMIN,
    ANALISTA,
    AUDITOR;

    public String asAuthority() {
        return "ROLE_" + name();
    }

    public boolean canDeleteLogically() {
        return this == ADMIN;
    }

    public boolean canRunRiskSimulation() {
        return this == ADMIN;
    }

    public boolean canWriteEntries() {
        return this == ADMIN || this == ANALISTA;
    }

    public boolean canReadAuditLogs() {
        return this == ADMIN || this == AUDITOR;
    }

    public boolean canExportReports() {
        return true;
    }

    public boolean isReadOnly() {
        return this == AUDITOR;
    }
}
