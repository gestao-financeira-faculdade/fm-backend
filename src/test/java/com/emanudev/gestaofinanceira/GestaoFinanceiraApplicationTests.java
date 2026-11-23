package com.emanudev.gestaofinanceira;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.emanudev.gestaofinanceira.simulation.adapter.in.web.dto.RunSimulationRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("isDockerAvailable")
class GestaoFinanceiraApplicationTests {

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void shouldRejectDateWithTimeButStillAcceptSeedAsString() {
        assertThatThrownBy(() -> jsonMapper.readValue("\"2026-12-31T10:00:00\"", LocalDate.class))
                .isInstanceOf(JacksonException.class);
        assertThat(jsonMapper.readValue("\"2026-12-31\"", LocalDate.class)).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(jsonMapper.readValue("{\"randomSeed\":\"8361509756792018453\"}", RunSimulationRequest.class).randomSeed())
                .isEqualTo(8361509756792018453L);
    }

    @Test
    void shouldBlockTruncateOnAuditLogs() {
        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE audit_logs"))
                .rootCause()
                .hasMessageContaining("audit_logs is append-only");
    }
}
