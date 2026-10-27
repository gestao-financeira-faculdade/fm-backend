package com.emanudev.gestaofinanceira.reporting.adapter.out.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.emanudev.gestaofinanceira.reporting.application.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.application.ReportRowItem;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PdfReportExporterTest {

    private final PdfReportExporter exporter = new PdfReportExporter();

    @Test
    void shouldGenerateValidPdf14BinaryWithHeaderFiltersTotalsAndEntries() {
        ReportRowItem receivable = new ReportRowItem(
                UUID.fromString("0192c8a4-0000-7000-8000-000000000101"),
                "RECEIVABLE",
                "RECEIVED",
                "Contrato Enterprise Anual",
                "Receita de Software",
                "Conta Principal",
                "Banco Horizonte S.A.",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 10),
                new BigDecimal("25000.00"),
                new BigDecimal("25000.00"),
                "BRL"
        );

        ReportRowItem payable = new ReportRowItem(
                UUID.fromString("0192c8a4-0000-7000-8000-000000000202"),
                "PAYABLE",
                "OVERDUE",
                "Cluster Cloud GPU",
                "Infraestrutura",
                "Conta Principal",
                "Cloud Provider Inc",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 15),
                null,
                new BigDecimal("8500.00"),
                null,
                "BRL"
        );

        ReportExportFilter filter = new ReportExportFilter(
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                "Cloud"
        );

        byte[] pdfBytes = exporter.export(List.of(receivable, payable), filter);

        assertThat(pdfBytes).isNotEmpty();
        String pdfText = new String(pdfBytes, StandardCharsets.ISO_8859_1);

        assertThat(pdfText)
                .startsWith("%PDF-1.4")
                .contains(PdfReportExporter.INSTITUTIONAL_TITLE)
                .contains("RESUMO DE FILTROS APLICADOS:")
                .contains("Busca: Cloud")
                .contains("Vencimento De: 2026-10-01 | Ate: 2026-10-31")
                .contains("Total de Lancamentos: 2")
                .contains("Total Contas a Receber \\(RECEIVABLE\\): BRL 25000.00")
                .contains("Total Contas a Pagar \\(PAYABLE\\): BRL 8500.00")
                .contains("Saldo Liquido do Relatorio: BRL 16500.00")
                .contains("Contrato Enterprise Anual")
                .contains("Cluster Cloud GPU")
                .contains("Hash SHA-256 de Integridade:")
                .endsWith("%%EOF\n");
    }

    @Test
    void shouldGenerateValidPdfWhenEntryListIsEmpty() {
        byte[] pdfBytes = exporter.export(List.of());
        String pdfText = new String(pdfBytes, StandardCharsets.ISO_8859_1);

        assertThat(pdfText)
                .startsWith("%PDF-1.4")
                .contains("Total de Lancamentos: 0")
                .contains("Nenhum lancamento encontrado para os filtros informados.")
                .endsWith("%%EOF\n");
    }
}
