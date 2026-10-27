package com.emanudev.gestaofinanceira.reporting.adapter.out.export;

import com.emanudev.gestaofinanceira.reporting.application.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.application.ReportRowItem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class PdfReportExporter {

    public static final String PDF_HEADER_SIGNATURE = "%PDF-1.4";
    public static final String INSTITUTIONAL_TITLE =
            "SISTEMA INTEGRADO DE GESTAO FINANCEIRA CORPORATIVA";
    public static final String REPORT_SUBTITLE =
            "RELATORIO AUDITAVEL DE LANCAMENTOS FINANCEIROS";

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
        lines.add("  Vencimento De: " + (filter.dueDateFrom() != null ? filter.dueDateFrom() : "INICIO")
                + " | Ate: " + (filter.dueDateTo() != null ? filter.dueDateTo() : "FIM"));
        lines.add("--------------------------------------------------------------------------------");

        BigDecimal totalReceivable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal totalPayable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal totalSettled = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        for (ReportRowItem row : rows) {
            if (row == null) {
                continue;
            }
            BigDecimal original = scale(row.originalAmount());
            if ("RECEIVABLE".equalsIgnoreCase(row.entryType())) {
                totalReceivable = totalReceivable.add(original);
            } else if ("PAYABLE".equalsIgnoreCase(row.entryType())) {
                totalPayable = totalPayable.add(original);
            }
            if (row.settledAmount() != null) {
                totalSettled = totalSettled.add(scale(row.settledAmount()));
            }
        }

        BigDecimal netBalance = totalReceivable.subtract(totalPayable).setScale(2, RoundingMode.HALF_EVEN);

        lines.add("TOTALIZADORES CONSOLIDADOS:");
        lines.add("  Total de Lancamentos: " + rows.size());
        lines.add("  Total Contas a Receber (RECEIVABLE): BRL " + totalReceivable.toPlainString());
        lines.add("  Total Contas a Pagar (PAYABLE): BRL " + totalPayable.toPlainString());
        lines.add("  Total Liquidado Acumulado: BRL " + totalSettled.toPlainString());
        lines.add("  Saldo Liquido do Relatorio: BRL " + netBalance.toPlainString());
        lines.add("--------------------------------------------------------------------------------");
        lines.add("LISTAGEM DE LANCAMENTOS:");
        lines.add("Vencimento | Tipo | Status | Descricao | Categoria | Contraparte | Valor Original | Liquidado");

        if (rows.isEmpty()) {
            lines.add("Nenhum lancamento encontrado para os filtros informados.");
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
        lines.add("Hash SHA-256 de Integridade: " + computeIntegrityHash(rows));
        return lines;
    }

    private byte[] buildPdfDocument(List<String> lines) {
        StringBuilder streamContent = new StringBuilder(2048);
        streamContent.append("BT\n");
        streamContent.append("/F1 9 Tf\n");
        streamContent.append("13 TL\n");
        streamContent.append("36 800 Td\n");

        for (int i = 0; i < lines.size(); i++) {
            String escaped = escapePdfText(lines.get(i));
            streamContent.append("(").append(escaped).append(") Tj\n");
            if (i < lines.size() - 1) {
                streamContent.append("T*\n");
            }
        }
        streamContent.append("ET\n");

        byte[] contentBytes = streamContent.toString().getBytes(StandardCharsets.ISO_8859_1);

        ByteArrayOutputStream pdf = new ByteArrayOutputStream(contentBytes.length + 1024);
        List<Integer> offsets = new ArrayList<>(6);

        writeAscii(pdf, PDF_HEADER_SIGNATURE + "\n");

        // Object 1: Catalog
        offsets.add(pdf.size());
        writeAscii(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        // Object 2: Pages
        offsets.add(pdf.size());
        writeAscii(pdf, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        // Object 3: Page
        offsets.add(pdf.size());
        writeAscii(
                pdf,
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n"
        );

        // Object 4: Font
        offsets.add(pdf.size());
        writeAscii(
                pdf,
                "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
        );

        // Object 5: Content Stream
        offsets.add(pdf.size());
        writeAscii(pdf, "5 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
        pdf.writeBytes(contentBytes);
        writeAscii(pdf, "endstream\nendobj\n");

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

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String sanitizePdfText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.replaceAll("[\\r\\n]+", " ").trim();
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
