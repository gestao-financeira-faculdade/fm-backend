package com.emanudev.gestaofinanceira.identity.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigJwtSecretTest {

    @Test
    @DisplayName("DEF-06: sem APP_JWT_SECRET a aplicação não sobe fora dos perfis dev e test")
    void shouldRefuseBuiltInSecretOutsideDevAndTest() {
        assertThatThrownBy(() -> SecurityConfig.validatedJwtSecret(SecurityConfig.DEV_DEFAULT_JWT_SECRET, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_JWT_SECRET is not set");

        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> SecurityConfig.validatedJwtSecret(SecurityConfig.DEV_DEFAULT_JWT_SECRET, prod))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("DEF-06: o segredo padrão é aceito só com os perfis dev ou test")
    void shouldAcceptBuiltInSecretInDevAndTest() {
        for (String profile : new String[]{"dev", "test"}) {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profile);
            assertThat(SecurityConfig.validatedJwtSecret(SecurityConfig.DEV_DEFAULT_JWT_SECRET, environment)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("DEF-06: segredo com menos de 32 bytes é recusado em qualquer perfil")
    void shouldRefuseShortSecret() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        assertThatThrownBy(() -> SecurityConfig.validatedJwtSecret("curto-demais", dev))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
        assertThat(SecurityConfig.validatedJwtSecret("x".repeat(32), new MockEnvironment())).hasSize(32);
    }
}
