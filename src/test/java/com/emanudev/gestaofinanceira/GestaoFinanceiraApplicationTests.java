package com.emanudev.gestaofinanceira;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("isDockerAvailable")
class GestaoFinanceiraApplicationTests {

    @Autowired
    private JsonMapper jsonMapper;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    void contextLoads() {
    }

    @Test
    void shouldRejectUnknownJsonPropertiesInRequestBodies() {
        assertThat(jsonMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }

    @Test
    void shouldSerializeValueObjectsWithoutDerivedFlags() {
        assertThat(jsonMapper.writeValueAsString(Money.brl("10.00"))).isEqualTo("{\"amount\":10.00,\"currency\":\"BRL\"}");
        assertThat(jsonMapper.writeValueAsString(Percentage.of("2.50"))).doesNotContain("zero").doesNotContain("positive");
    }
}
