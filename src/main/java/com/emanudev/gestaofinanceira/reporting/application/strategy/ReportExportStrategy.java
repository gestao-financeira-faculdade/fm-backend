package com.emanudev.gestaofinanceira.reporting.application.strategy;

import com.emanudev.gestaofinanceira.reporting.application.ExportedReport;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFormat;
import com.emanudev.gestaofinanceira.reporting.application.ReportRowItem;

import java.util.List;

public interface ReportExportStrategy {

    ExportedReport exportReport(List<ReportRowItem> rows, ReportExportFilter filter);

    ReportExportFormat getFormat();
}
