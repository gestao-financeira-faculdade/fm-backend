package com.emanudev.gestaofinanceira.reporting.adapter.in.web;

import com.emanudev.gestaofinanceira.reporting.adapter.in.web.mapper.ReportExportWebMapper;
import com.emanudev.gestaofinanceira.reporting.adapter.out.export.CsvStreamingExporter;
import com.emanudev.gestaofinanceira.reporting.adapter.out.export.PdfReportExporter;
import com.emanudev.gestaofinanceira.reporting.application.ExportEntriesReportUseCase;
import com.emanudev.gestaofinanceira.reporting.domain.model.CategorySpendItem;
import com.emanudev.gestaofinanceira.reporting.domain.model.DashboardSummary;
import com.emanudev.gestaofinanceira.reporting.domain.model.MonthlyCashFlowPoint;
import com.emanudev.gestaofinanceira.reporting.domain.model.ReportExportEntryRow;
import com.emanudev.gestaofinanceira.reporting.domain.port.in.GetDashboardSummaryQuery;
import com.emanudev.gestaofinanceira.reporting.domain.port.out.ReportingReadPort;
import com.emanudev.gestaofinanceira.shared.adapter.in.web.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    @Test
    @DisplayName("GET /v1/dashboard/summary returns executive KPIs, monthly series, and category breakdown")
    void shouldReturnDashboardSummaryJson() throws Exception {
        GetDashboardSummaryQuery summaryQuery = mock(GetDashboardSummaryQuery.class);
        DashboardSummary summary = new DashboardSummary(
                new BigDecimal("125000.00"),
                new BigDecimal("42000.00"),
                new BigDecimal("18000.00"),
                new BigDecimal("3500.00"),
                149000.00d,
                List.of(
                        new MonthlyCashFlowPoint(
                                YearMonth.of(2026, 3),
                                new BigDecimal("55000.00"),
                                new BigDecimal("31000.00"),
                                new BigDecimal("24000.00")
                        )
                ),
                List.of(
                        new CategorySpendItem(
                                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                                "Cloud Infrastructure",
                                "EXPENSE",
                                new BigDecimal("12400.00"),
                                4L
                        )
                ),
                Instant.parse("2026-03-31T12:00:00Z")
        );
        when(summaryQuery.getSummary(any(), any(), any())).thenReturn(summary);

        DashboardController controller = new DashboardController(summaryQuery);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/v1/dashboard/summary")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-03-31")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realizedBalance").value(125000.00))
                .andExpect(jsonPath("$.accountsReceivablePending").value(42000.00))
                .andExpect(jsonPath("$.accountsPayablePending").value(18000.00))
                .andExpect(jsonPath("$.overdueTotal").value(3500.00))
                .andExpect(jsonPath("$.projectedBalance30d").value(149000.00))
                .andExpect(jsonPath("$.monthlyCashFlow[0].month").value("2026-03"))
                .andExpect(jsonPath("$.monthlyCashFlow[0].netCashFlow").value(24000.00))
                .andExpect(jsonPath("$.categoryBreakdown[0].categoryName").value("Cloud Infrastructure"));
    }

    @Test
    @DisplayName("GET /v1/dashboard/summary returns 400 ProblemDetail when startDate is after endDate")
    void shouldReturn400WhenDateRangeIsInvalid() throws Exception {
        GetDashboardSummaryQuery summaryQuery = mock(GetDashboardSummaryQuery.class);
        when(summaryQuery.getSummary(any(), any(), any())).thenCallRealMethod();

        ReportingReadPort readPort = mock(ReportingReadPort.class);
        com.emanudev.gestaofinanceira.reporting.application.GetDashboardSummaryUseCase useCase =
                new com.emanudev.gestaofinanceira.reporting.application.GetDashboardSummaryUseCase(readPort);

        DashboardController controller = new DashboardController(useCase);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/v1/dashboard/summary")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-01-01")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_ARGUMENT"));
    }

    @Test
    @DisplayName("GET /v1/reports/entries/export?format=CSV streams UTF-8 BOM CSV with attachment header")
    void shouldExportCsvViaGetEndpoint() throws Exception {
        ReportingReadPort readPort = mock(ReportingReadPort.class);
        ReportExportEntryRow sampleRow = new ReportExportEntryRow(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "PAYABLE",
                "SETTLED",
                "=1+1 Malicious Supplier",
                "Infrastructure",
                "Main Account",
                "Main Bank",
                "BRL",
                new BigDecimal("1500.00"),
                new BigDecimal("1500.00"),
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 15),
                Instant.parse("2026-03-14T15:00:00Z")
        );
        when(readPort.streamEntriesForExport(any())).thenAnswer(inv -> Stream.of(sampleRow));

        CsvStreamingExporter csvExporter = new CsvStreamingExporter();
        PdfReportExporter pdfExporter = new PdfReportExporter();
        ExportEntriesReportUseCase useCase = new ExportEntriesReportUseCase(readPort, List.of(csvExporter, pdfExporter));
        ReportExportWebMapper mapper = new ReportExportWebMapper();
        ReportExportController controller = new ReportExportController(useCase, mapper);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        byte[] body = mockMvc.perform(get("/v1/reports/entries/export")
                        .param("format", "CSV")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);
        String csvText = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertThat(csvText).contains("'=1+1 Malicious Supplier");
    }

    @Test
    @DisplayName("POST /v1/reports/entries/export with format=PDF returns valid PDF document")
    void shouldExportPdfViaPostEndpoint() throws Exception {
        ReportingReadPort readPort = mock(ReportingReadPort.class);
        when(readPort.streamEntriesForExport(any())).thenAnswer(inv -> Stream.empty());

        CsvStreamingExporter csvExporter = new CsvStreamingExporter();
        PdfReportExporter pdfExporter = new PdfReportExporter();
        ExportEntriesReportUseCase useCase = new ExportEntriesReportUseCase(readPort, List.of(csvExporter, pdfExporter));
        ReportExportWebMapper mapper = new ReportExportWebMapper();
        ReportExportController controller = new ReportExportController(useCase, mapper);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        byte[] body = mockMvc.perform(post("/v1/reports/entries/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"PDF\",\"startDate\":\"2026-03-01\",\"endDate\":\"2026-03-31\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String header = new String(body, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("GET /v1/reports/entries/export returns 400 ProblemDetail for unsupported format")
    void shouldReturn400ForUnsupportedFormat() throws Exception {
        ReportingReadPort readPort = mock(ReportingReadPort.class);
        CsvStreamingExporter csvExporter = new CsvStreamingExporter();
        PdfReportExporter pdfExporter = new PdfReportExporter();
        ExportEntriesReportUseCase useCase = new ExportEntriesReportUseCase(readPort, List.of(csvExporter, pdfExporter));
        ReportExportWebMapper mapper = new ReportExportWebMapper();
        ReportExportController controller = new ReportExportController(useCase, mapper);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/v1/reports/entries/export")
                        .param("format", "XLSX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_ARGUMENT"));
    }
}
