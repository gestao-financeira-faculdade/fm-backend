package com.emanudev.gestaofinanceira.reporting.adapter.out.export;

import com.emanudev.gestaofinanceira.reporting.application.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.application.ExportedReport;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFormat;
import com.emanudev.gestaofinanceira.reporting.application.ReportRowItem;
import com.emanudev.gestaofinanceira.reporting.application.strategy.ReportExportStrategy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PdfReportExporter implements ReportExportStrategy {

    public static final String CONTENT_TYPE = "application/pdf";
    public static final String DEFAULT_FILENAME = "relatorio-financeiro.pdf";

    @Override
    public ExportedReport exportReport(List<ReportRowItem> rows, ReportExportFilter filter) {
        return new ExportedReport(export(rows, filter), CONTENT_TYPE, ExportFileNames.forToday("pdf"));
    }

    @Override
    public ReportExportFormat getFormat() {
        return ReportExportFormat.PDF;
    }

    public static final String PDF_HEADER_SIGNATURE = "%PDF-1.4";
    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    public static final String INSTITUTIONAL_TITLE =
            "SISTEMA INTEGRADO DE GESTÃO FINANCEIRA CORPORATIVA";
    public static final String REPORT_SUBTITLE =
            "RELATÓRIO AUDITÁVEL DE LANÇAMENTOS FINANCEIROS";
    static final int LINES_PER_PAGE = 56;

    public byte[] export(List<ReportRowItem> rows) {
        return export(rows, ReportExportFilter.empty());
    }

    public byte[] export(List<ReportRowItem> rows, ReportExportFilter filter) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(4096);
        export(rows, filter, outputStream);
        return outputStream.toByteArray();
    }

    public void export(List<ReportRowItem> rows, OutputStream outputStream) {
        export(rows, ReportExportFilter.empty(), outputStream);
    }

    public void export(List<ReportRowItem> rows, ReportExportFilter filter, OutputStream outputStream) {
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        List<ReportRowItem> safeRows = rows != null ? rows : List.of();
        ReportExportFilter effectiveFilter = filter != null ? filter : ReportExportFilter.empty();

        List<String> lines = buildReportLines(safeRows, effectiveFilter);
        byte[] pdfBytes = buildPdfDocument(lines);

        try {
            outputStream.write(pdfBytes);
            outputStream.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write PDF report", ex);
        }
    }

    private List<String> buildReportLines(List<ReportRowItem> rows, ReportExportFilter filter) {
        List<String> lines = new ArrayList<>();
        lines.add(INSTITUTIONAL_TITLE);
        lines.add(REPORT_SUBTITLE);
        lines.add("--------------------------------------------------------------------------------");
        lines.add("RESUMO DE FILTROS APLICADOS:");
        lines.add("  Tipo: " + valueOrAll(filter.entryType())
                + " | Status: " + valueOrAll(filter.status())
                + " | Busca: " + valueOrAll(filter.search()));
        lines.add("  Categoria ID: " + (filter.categoryId() != null ? filter.categoryId() : "TODAS")
                + " | Conta ID: " + (filter.accountId() != null ? filter.accountId() : "TODAS"));
        lines.add("  Vencimento de: " + (filter.dueDateFrom() != null ? filter.dueDateFrom() : "INICIO")
                + " | Até: " + (filter.dueDateTo() != null ? filter.dueDateTo() : "FIM"));
        lines.add("--------------------------------------------------------------------------------");

        BigDecimal totalReceivable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal totalPayable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal totalSettled = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        for (ReportRowItem row : rows) {
            if (row == null) {
                continue;
            }
            BigDecimal exposure = exposure(row);
            if ("RECEIVABLE".equalsIgnoreCase(row.entryType())) {
                totalReceivable = totalReceivable.add(exposure);
            } else if ("PAYABLE".equalsIgnoreCase(row.entryType())) {
                totalPayable = totalPayable.add(exposure);
            }
            if (row.settledAmount() != null) {
                totalSettled = totalSettled.add(scale(row.settledAmount()));
            }
        }

        BigDecimal netBalance = totalReceivable.subtract(totalPayable).setScale(2, RoundingMode.HALF_EVEN);

        lines.add("TOTALIZADORES CONSOLIDADOS:");
        lines.add("  Total de lançamentos: " + rows.size());
        lines.add("  Total Contas a Receber (RECEIVABLE): BRL " + totalReceivable.toPlainString());
        lines.add("  Total Contas a Pagar (PAYABLE): BRL " + totalPayable.toPlainString());
        lines.add("  Total liquidado acumulado: BRL " + totalSettled.toPlainString());
        lines.add("  Saldo líquido do relatório: BRL " + netBalance.toPlainString());
        lines.add("--------------------------------------------------------------------------------");
        lines.add("LISTAGEM DE LANÇAMENTOS:");
        lines.add("Vencimento | Tipo | Status | Descrição | Categoria | Contraparte | Valor original | Liquidado");

        if (rows.isEmpty()) {
            lines.add("Nenhum lançamento encontrado para os filtros informados.");
        } else {
            for (ReportRowItem row : rows) {
                if (row == null) {
                    continue;
                }
                String line = String.format(
                        Locale.ROOT,
                        "%s | %s | %s | %s | %s | %s | %s %s | %s",
                        row.dueDate(),
                        row.entryType(),
                        row.status(),
                        sanitizePdfText(row.description()),
                        sanitizePdfText(row.categoryName()),
                        sanitizePdfText(row.counterpartyName() != null ? row.counterpartyName() : "-"),
                        row.currency(),
                        scale(row.originalAmount()).toPlainString(),
                        row.settledAmount() != null ? scale(row.settledAmount()).toPlainString() : "-"
                );
                lines.add(line);
            }
        }

        lines.add("--------------------------------------------------------------------------------");
        lines.add("Hash SHA-256 de integridade: " + computeIntegrityHash(rows));
        return lines;
    }

    private byte[] buildPdfDocument(List<String> lines) {
        List<List<String>> pages = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += LINES_PER_PAGE) {
            pages.add(lines.subList(start, Math.min(lines.size(), start + LINES_PER_PAGE)));
        }
        if (pages.isEmpty()) {
            pages.add(List.of());
        }

        int pageCount = pages.size();
        int fontObject = 3;
        int firstPageObject = 4;
        ByteArrayOutputStream pdf = new ByteArrayOutputStream(4096 + lines.size() * 120);
        List<Integer> offsets = new ArrayList<>();

        writeAscii(pdf, PDF_HEADER_SIGNATURE + "\n");

        offsets.add(pdf.size());
        writeAscii(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            kids.append(firstPageObject + i * 2).append(" 0 R ");
        }
        offsets.add(pdf.size());
        writeAscii(pdf, "2 0 obj\n<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pageCount + " >>\nendobj\n");

        offsets.add(pdf.size());
        writeAscii(pdf, fontObject + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                + "/Encoding /WinAnsiEncoding >>\nendobj\n");

        for (int i = 0; i < pageCount; i++) {
            int pageObject = firstPageObject + i * 2;
            int contentObject = pageObject + 1;
            byte[] content = pageContent(pages.get(i), i + 1, pageCount);

            offsets.add(pdf.size());
            writeAscii(pdf, pageObject + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /Font << /F1 " + fontObject + " 0 R >> >> /Contents " + contentObject + " 0 R >>\nendobj\n");

            offsets.add(pdf.size());
            writeAscii(pdf, contentObject + " 0 obj\n<< /Length " + content.length + " >>\nstream\n");
            pdf.writeBytes(content);
            writeAscii(pdf, "endstream\nendobj\n");
        }

        int xrefOffset = pdf.size();
        writeAscii(pdf, "xref\n0 " + (offsets.size() + 1) + "\n");
        writeAscii(pdf, "0000000000 65535 f \n");
        for (int offset : offsets) {
            writeAscii(pdf, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        writeAscii(
                pdf,
                "trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\n"
                        + "startxref\n" + xrefOffset + "\n%%EOF\n"
        );
        return pdf.toByteArray();
    }

    private static byte[] pageContent(List<String> pageLines, int pageNumber, int pageCount) {
        StringBuilder stream = new StringBuilder(2048);
        stream.append("BT\n/F1 9 Tf\n13 TL\n36 800 Td\n");
        for (int i = 0; i < pageLines.size(); i++) {
            stream.append("(").append(escapePdfText(pageLines.get(i))).append(") Tj\n");
            if (i < pageLines.size() - 1) {
                stream.append("T*\n");
            }
        }
        stream.append("ET\n");
        stream.append("BT\n/F1 8 Tf\n500 24 Td\n(")
                .append(escapePdfText("Página " + pageNumber + " de " + pageCount))
                .append(") Tj\nET\n");
        return stream.toString().getBytes(WIN_ANSI);
    }

    /**
     * What the entry still represents for the totals: the settled part of a paid parent plus its open residual
     * child add up to the original amount once, instead of counting the parent's original and the child again.
     */
    private static BigDecimal exposure(ReportRowItem row) {
        if ("CANCELLED".equalsIgnoreCase(row.status())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }
        return row.settledAmount() != null ? scale(row.settledAmount()) : scale(row.originalAmount());
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String sanitizePdfText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String composed = Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll("[\\r\\n]+", " ").trim();
        StringBuilder printable = new StringBuilder(composed.length());
        composed.codePoints().forEach(cp -> printable.append(WIN_ANSI.newEncoder().canEncode(Character.toString(cp))
                ? Character.toString(cp)
                : "?"));
        return printable.toString();
    }

    private static String escapePdfText(String text) {
        String normalized = sanitizePdfText(text);
        return normalized
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private static String valueOrAll(String value) {
        return (value == null || value.isBlank()) ? "TODOS" : sanitizePdfText(value);
    }

    private static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static String computeIntegrityHash(List<ReportRowItem> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ReportRowItem row : rows) {
                if (row != null) {
                    digest.update(row.id().toString().getBytes(StandardCharsets.UTF_8));
                    digest.update(scale(row.originalAmount()).toPlainString().getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            return "N/A";
        }
    }
}
