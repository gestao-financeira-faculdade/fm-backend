package com.emanudev.gestaofinanceira.reporting.adapter.out.export;

import java.nio.charset.Charset;
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
        String pdfText = new String(pdfBytes, Charset.forName("windows-1252"));

        assertThat(pdfText)
                .startsWith("%PDF-1.4")
                .contains(PdfReportExporter.INSTITUTIONAL_TITLE)
                .contains("/Encoding /WinAnsiEncoding")
                .contains("RESUMO DE FILTROS APLICADOS:")
                .contains("Busca: Cloud")
                .contains("Vencimento de: 2026-10-01 | Até: 2026-10-31")
                .contains("Total de lançamentos: 2")
                .contains("Total Contas a Receber \\(RECEIVABLE\\): BRL 25000.00")
                .contains("Total Contas a Pagar \\(PAYABLE\\): BRL 8500.00")
                .contains("Saldo líquido do relatório: BRL 16500.00")
                .contains("Contrato Enterprise Anual")
                .contains("Cluster Cloud GPU")
                .contains("Hash SHA-256 de integridade:")
                .contains("Página 1 de 1")
                .endsWith("%%EOF\n");
    }

    @Test
    void shouldGenerateValidPdfWhenEntryListIsEmpty() {
        byte[] pdfBytes = exporter.export(List.of());
        String pdfText = new String(pdfBytes, Charset.forName("windows-1252"));

        assertThat(pdfText)
                .startsWith("%PDF-1.4")
                .contains("Total de lançamentos: 0")
                .contains("Nenhum lançamento encontrado para os filtros informados.")
                .endsWith("%%EOF\n");
    }

    @Test
    void shouldPaginateLongReportsAndKeepIntegrityHashOnLastPage() {
        List<ReportRowItem> rows = java.util.stream.IntStream.range(0, 120)
                .mapToObj(i -> new ReportRowItem(
                        UUID.randomUUID(), "PAYABLE", "PENDING", "Lançamento " + i, "Serviços", "Conta", null,
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, new BigDecimal("10.00"), null, "BRL"))
                .toList();

        String pdfText = new String(exporter.export(rows), Charset.forName("windows-1252"));

        assertThat(pdfText).contains("/Count 3").contains("Página 3 de 3");
        assertThat(pdfText.indexOf("Hash SHA-256 de integridade:")).isGreaterThan(pdfText.indexOf("Página 2 de 3"));
    }

    @Test
    void shouldCountSplitOnceInPayableTotalAndRenderLatin1Accents() {
        ReportRowItem paidParent = new ReportRowItem(
                UUID.randomUUID(), "PAYABLE", "PAID", "Manutenção", "Serviços", "Conta", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 20),
                new BigDecimal("1000.00"), new BigDecimal("400.00"), "BRL");
        ReportRowItem residualChild = new ReportRowItem(
                UUID.randomUUID(), "PAYABLE", "PENDING", "Manutenção (residual)", "Serviços", "Conta", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null,
                new BigDecimal("600.00"), null, "BRL");
        ReportRowItem cancelled = new ReportRowItem(
                UUID.randomUUID(), "PAYABLE", "CANCELLED", "Cancelado 日本", "Serviços", "Conta", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null,
                new BigDecimal("999.00"), null, "BRL");

        String pdfText = new String(exporter.export(List.of(paidParent, residualChild, cancelled)), Charset.forName("windows-1252"));

        assertThat(pdfText)
                .contains("Total Contas a Pagar \\(PAYABLE\\): BRL 1000.00")
                .contains("Manutenção")
                .contains("Cancelado ??");
    }
}
