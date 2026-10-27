package com.emanudev.gestaofinanceira.identity.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    @Test
    @DisplayName("ADMIN deve possuir permissão total na matriz RBAC")
    void adminShouldHaveFullPermissions() {
        Role role = Role.ADMIN;

        assertThat(role.asAuthority()).isEqualTo("ROLE_ADMIN");
        assertThat(role.canDeleteLogically()).isTrue();
        assertThat(role.canRunRiskSimulation()).isTrue();
        assertThat(role.canWriteEntries()).isTrue();
        assertThat(role.canReadAuditLogs()).isTrue();
        assertThat(role.canExportReports()).isTrue();
        assertThat(role.isReadOnly()).isFalse();
    }

    @Test
    @DisplayName("ANALISTA deve poder operar lançamentos, mas não deletar logicamente, simular risco ou ler logs de auditoria")
    void analistaShouldHaveOperationalPermissionsOnly() {
        Role role = Role.ANALISTA;

        assertThat(role.asAuthority()).isEqualTo("ROLE_ANALISTA");
        assertThat(role.canWriteEntries()).isTrue();
        assertThat(role.canExportReports()).isTrue();
        assertThat(role.canDeleteLogically()).isFalse();
        assertThat(role.canRunRiskSimulation()).isFalse();
        assertThat(role.canReadAuditLogs()).isFalse();
        assertThat(role.isReadOnly()).isFalse();
    }

    @Test
    @DisplayName("AUDITOR deve ser estritamente somente-leitura para operações financeiras e ter acesso à trilha de auditoria")
    void auditorShouldHaveReadOnlyAndAuditPermissions() {
        Role role = Role.AUDITOR;

        assertThat(role.asAuthority()).isEqualTo("ROLE_AUDITOR");
        assertThat(role.canReadAuditLogs()).isTrue();
        assertThat(role.canExportReports()).isTrue();
        assertThat(role.isReadOnly()).isTrue();
        assertThat(role.canWriteEntries()).isFalse();
        assertThat(role.canDeleteLogically()).isFalse();
        assertThat(role.canRunRiskSimulation()).isFalse();
    }

    @Test
    @DisplayName("User deve normalizar e-mail para minúsculas e suportar ativação/desativação imutável")
    void userShouldNormalizeEmailAndToggleActiveState() {
        Instant t0 = Instant.parse("2026-03-01T10:00:00Z");
        Instant t1 = Instant.parse("2026-03-01T12:00:00Z");
        UUID id = UUID.randomUUID();

        User user = new User(
                id,
                "  Maria Silva  ",
                "  MARIA.SILVA@GestaoFinanceira.com ",
                "$2a$10$hash",
                Role.ANALISTA,
                true,
                t0,
                t0
        );

        assertThat(user.fullName()).isEqualTo("Maria Silva");
        assertThat(user.email()).isEqualTo("maria.silva@gestaofinanceira.com");
        assertThat(user.isActive()).isTrue();

        User deactivated = user.deactivate(t1);
        assertThat(deactivated.isActive()).isFalse();
        assertThat(deactivated.updatedAt()).isEqualTo(t1);

        User reactivated = deactivated.activate(t1.plusSeconds(60));
        assertThat(reactivated.isActive()).isTrue();
    }

    @Test
    @DisplayName("RefreshToken deve validar expiração e revogação de forma determinística")
    void refreshTokenShouldValidateExpirationAndRevocation() {
        Instant createdAt = Instant.parse("2026-03-01T10:00:00Z");
        Instant expiresAt = createdAt.plusSeconds(3600);

        RefreshToken token = new RefreshToken(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "sha256-hash-value",
                expiresAt,
                null,
                createdAt
        );

        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isExpired(createdAt.plusSeconds(1800))).isFalse();
        assertThat(token.isValid(createdAt.plusSeconds(1800))).isTrue();
        assertThat(token.isExpired(expiresAt)).isTrue();
        assertThat(token.isValid(expiresAt)).isFalse();

        Instant revokedAt = createdAt.plusSeconds(600);
        RefreshToken revoked = token.revoke(revokedAt);
        assertThat(revoked.isRevoked()).isTrue();
        assertThat(revoked.revokedAt()).isEqualTo(revokedAt);
        assertThat(revoked.isValid(createdAt.plusSeconds(601))).isFalse();

        assertThatThrownBy(() -> new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "  ", expiresAt, null, createdAt))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
