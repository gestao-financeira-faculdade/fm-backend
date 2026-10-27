package com.emanudev.gestaofinanceira.reporting.adapter.out.export;

import com.emanudev.gestaofinanceira.reporting.application.ExportedReport;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFormat;
import com.emanudev.gestaofinanceira.reporting.application.ReportRowItem;
import com.emanudev.gestaofinanceira.reporting.application.strategy.ReportExportStrategy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class CsvStreamingExporter implements ReportExportStrategy {

    public static final String CONTENT_TYPE = "text/csv;charset=UTF-8";
    public static final String DEFAULT_FILENAME = "lancamentos-financeiros.csv";

    @Override
    public ExportedReport exportReport(List<ReportRowItem> rows, ReportExportFilter filter) {
        return new ExportedReport(export(rows), CONTENT_TYPE, DEFAULT_FILENAME);
    }

    @Override
    public ReportExportFormat getFormat() {
        return ReportExportFormat.CSV;
    }

    public static final String UTF8_BOM = "\uFEFF";
    public static final char DELIMITER = ';';
    private static final String LINE_SEPARATOR = "\r\n";

    private static final String HEADER = String.join(
            String.valueOf(DELIMITER),
            "ID",
            "Tipo",
            "Status",
            "Descricao",
            "Categoria",
            "Conta",
            "Contraparte",
            "Data Competencia",
            "Data Vencimento",
            "Data Liquidacao",
            "Valor Original",
            "Valor Liquidado",
            "Moeda"
    );

    public byte[] export(List<ReportRowItem> rows) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1024);
        export(rows, outputStream);
        return outputStream.toByteArray();
    }

    public String exportToString(List<ReportRowItem> rows) {
        return new String(export(rows), StandardCharsets.UTF_8);
    }

    public void export(List<ReportRowItem> rows, OutputStream outputStream) {
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        List<ReportRowItem> safeRows = rows != null ? rows : List.of();

        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writer.write(UTF8_BOM);
            writer.write(HEADER);
            writer.write(LINE_SEPARATOR);

            for (ReportRowItem row : safeRows) {
                if (row == null) {
                    continue;
                }
                writeRow(writer, row);
            }
            writer.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to stream CSV report", ex);
        }
    }

    private void writeRow(Writer writer, ReportRowItem row) throws IOException {
        writer.write(formatScalar(row.id() != null ? row.id().toString() : ""));
        writer.write(DELIMITER);
        writer.write(formatTextCell(row.entryType()));
        writer.write(DELIMITER);
        writer.write(formatTextCell(row.status()));
        writer.write(DELIMITER);
        writer.write(formatTextCell(row.description()));
        writer.write(DELIMITER);
        writer.write(formatTextCell(row.categoryName()));
        writer.write(DELIMITER);
        writer.write(formatTextCell(row.accountName()));
        writer.write(DELIMITER);
        writer.write(formatTextCell(row.counterpartyName()));
        writer.write(DELIMITER);
        writer.write(formatDate(row.competenceDate()));
        writer.write(DELIMITER);
        writer.write(formatDate(row.dueDate()));
        writer.write(DELIMITER);
        writer.write(formatDate(row.settlementDate()));
        writer.write(DELIMITER);
        writer.write(formatAmount(row.originalAmount()));
        writer.write(DELIMITER);
        writer.write(formatAmount(row.settledAmount()));
        writer.write(DELIMITER);
        writer.write(formatTextCell(row.currency()));
        writer.write(LINE_SEPARATOR);
    }

    public static String sanitizeFormulaInjection(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char firstChar = value.charAt(0);
        if (isFormulaTrigger(firstChar)) {
            return "'" + value;
        }
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && isFormulaTrigger(trimmed.charAt(0))) {
            return "'" + value;
        }
        return value;
    }

    public static String formatTextCell(String value) {
        String sanitized = sanitizeFormulaInjection(value);
        if (sanitized.isEmpty()) {
            return "";
        }
        boolean requiresQuoting = sanitized.indexOf(DELIMITER) >= 0
                || sanitized.indexOf('"') >= 0
                || sanitized.indexOf('\n') >= 0
                || sanitized.indexOf('\r') >= 0;
        if (!requiresQuoting) {
            return sanitized;
        }
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }

    private static boolean isFormulaTrigger(char c) {
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
    }

    private static String formatScalar(String value) {
        return value != null ? value : "";
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.toString() : "";
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
