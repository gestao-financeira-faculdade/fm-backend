package com.emanudev.gestaofinanceira.reporting.application;

import com.emanudev.gestaofinanceira.reporting.domain.model.ReportExportEntryRow;
import com.emanudev.gestaofinanceira.reporting.domain.model.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.domain.model.ReportExportFormat;
import com.emanudev.gestaofinanceira.reporting.domain.port.in.ExportEntriesQuery;
import com.emanudev.gestaofinanceira.reporting.domain.port.out.ReportExportPort;
import com.emanudev.gestaofinanceira.reporting.domain.port.out.ReportingReadPort;
import com.emanudev.gestaofinanceira.shared.exception.InvalidArgumentDomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class ExportEntriesReportUseCase implements ExportEntriesQuery {

    private final ReportingReadPort reportingReadPort;
    private final Map<ReportExportFormat, ReportExportPort> exportersByFormat;

    public ExportEntriesReportUseCase(
            ReportingReadPort reportingReadPort,
            List<ReportExportPort> exporters
    ) {
        this.reportingReadPort = Objects.requireNonNull(reportingReadPort, "reportingReadPort must not be null");
        Objects.requireNonNull(exporters, "exporters must not be null");
        this.exportersByFormat = new EnumMap<>(ReportExportFormat.class);
        for (ReportExportPort exporter : exporters) {
            this.exportersByFormat.put(exporter.supportedFormat(), exporter);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void exportToStream(ReportExportFormat format, ReportExportFilter filter, OutputStream outputStream) {
        ReportExportPort exporter = resolveExporter(format);
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(outputStream, "outputStream must not be null");

        try (Stream<ReportExportEntryRow> rows = reportingReadPort.streamEntriesForExport(filter)) {
            exporter.export(rows, filter, outputStream);
        }
    }

    @Transactional(readOnly = true)
    public ExportedReport export(ReportExportFormat format, ReportExportFilter filter) {
        ReportExportPort exporter = resolveExporter(format);
        Objects.requireNonNull(filter, "filter must not be null");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(8192);
        try (Stream<ReportExportEntryRow> rows = reportingReadPort.streamEntriesForExport(filter)) {
            exporter.export(rows, filter, outputStream);
        }

        return new ExportedReport(
                outputStream.toByteArray(),
                exporter.contentType(),
                exporter.suggestedFilename(filter)
        );
    }

    private ReportExportPort resolveExporter(ReportExportFormat format) {
        Objects.requireNonNull(format, "format must not be null");
        ReportExportPort exporter = exportersByFormat.get(format);
        if (exporter == null) {
            throw new InvalidArgumentDomainException("Unsupported export format: " + format);
        }
        return exporter;
    }
}
