package com.emanudev.gestaofinanceira.reporting.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emanudev.gestaofinanceira.reporting.adapter.out.export.CsvStreamingExporter;
import com.emanudev.gestaofinanceira.reporting.adapter.out.export.PdfReportExporter;
import com.emanudev.gestaofinanceira.reporting.application.DailyPercentileBand;
import com.emanudev.gestaofinanceira.reporting.application.ExecutiveKpiResponse;
import com.emanudev.gestaofinanceira.reporting.application.ExportEntriesReportUseCase;
import com.emanudev.gestaofinanceira.reporting.application.GetExecutiveKpisUseCase;
import com.emanudev.gestaofinanceira.reporting.application.GetUncertaintyConeUseCase;
import com.emanudev.gestaofinanceira.reporting.application.HistoricalBalancePoint;
import com.emanudev.gestaofinanceira.reporting.application.ProjectedSimulationCone;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.application.ReportRowItem;
import com.emanudev.gestaofinanceira.reporting.application.UncertaintyConeResponse;
import com.emanudev.gestaofinanceira.shared.adapter.in.web.GlobalExceptionHandler;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private GetExecutiveKpisUseCase getExecutiveKpisUseCase;

    @Mock
    private GetUncertaintyConeUseCase getUncertaintyConeUseCase;

    @Mock
    private ExportEntriesReportUseCase exportEntriesReportUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DashboardController dashboardController = new DashboardController(
                getExecutiveKpisUseCase,
                getUncertaintyConeUseCase
        );
        ReportExportController reportExportController = new ReportExportController(
                exportEntriesReportUseCase,
                new CsvStreamingExporter(),
                new PdfReportExporter()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(dashboardController, reportExportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnExecutiveKpisForCurrentDate() throws Exception {
        LocalDate referenceDate = LocalDate.of(2026, 10, 26);
        Instant generatedAt = Instant.parse("2026-10-26T20:00:00Z");

        ExecutiveKpiResponse response = new ExecutiveKpiResponse(
                Money.brl("154320.50"),
                Money.brl("18200.00"),
                Money.brl("9450.75"),
                Percentage.of("4.2500"),
                referenceDate,
                generatedAt
        );

        when(getExecutiveKpisUseCase.execute()).thenReturn(response);

        mockMvc.perform(get("/v1/dashboard/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consolidatedBalance.amount").value(154320.50))
                .andExpect(jsonPath("$.consolidatedBalance.currency").value("BRL"))
                .andExpect(jsonPath("$.totalReceivableToday.amount").value(18200.00))
                .andExpect(jsonPath("$.totalPayableToday.amount").value(9450.75))
                .andExpect(jsonPath("$.monthlyDelinquencyRatePct.value").value(4.25))
                .andExpect(jsonPath("$.referenceDate").value("2026-10-26"));

        verify(getExecutiveKpisUseCase).execute();
    }

    @Test
    void shouldReturnExecutiveKpisForExplicitReferenceDate() throws Exception {
        LocalDate referenceDate = LocalDate.of(2026, 10, 15);
        ExecutiveKpiResponse response = new ExecutiveKpiResponse(
                Money.brl("100000.00"),
                Money.brl("5000.00"),
                Money.brl("2000.00"),
                Percentage.zero(),
                referenceDate,
                Instant.parse("2026-10-15T12:00:00Z")
        );

        when(getExecutiveKpisUseCase.execute(referenceDate)).thenReturn(response);

        mockMvc.perform(get("/v1/dashboard/kpis")
                        .param("referenceDate", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consolidatedBalance.amount").value(100000.00))
                .andExpect(jsonPath("$.referenceDate").value("2026-10-15"));

        verify(getExecutiveKpisUseCase).execute(referenceDate);
    }

    @Test
    void shouldReturnUncertaintyConeWithHistoricalAndProjectedSeries() throws Exception {
        UUID simulationId = UUID.fromString("0192c8a4-0000-7000-8000-000000000999");
        LocalDate today = LocalDate.of(2026, 10, 26);

        HistoricalBalancePoint histPoint = new HistoricalBalancePoint(today, Money.brl("80000.00"));
        DailyPercentileBand bandDay1 = new DailyPercentileBand(
                1,
                today.plusDays(1),
                new BigDecimal("-5000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("45000.00"),
                new BigDecimal("78000.00"),
                new BigDecimal("95000.00"),
                new BigDecimal("110000.00")
        );
        ProjectedSimulationCone cone = new ProjectedSimulationCone(
                simulationId,
                30,
                100_000,
                Money.brl("-5000.00"),
                Money.brl("78000.00"),
                Percentage.of("6.5000"),
                "OpenCL GPU",
                Instant.parse("2026-10-26T20:10:00Z"),
                List.of(bandDay1)
        );

        when(getUncertaintyConeUseCase.execute(30, simulationId))
                .thenReturn(new UncertaintyConeResponse(List.of(histPoint), Optional.of(cone)));

        mockMvc.perform(get("/v1/dashboard/uncertainty-cone")
                        .param("historyDays", "30")
                        .param("simulationId", simulationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historicalSeries.length()").value(1))
                .andExpect(jsonPath("$.historicalSeries[0].balance.amount").value(80000.00))
                .andExpect(jsonPath("$.projectionCone.simulationId").value(simulationId.toString()))
                .andExpect(jsonPath("$.projectionCone.horizonDays").value(30))
                .andExpect(jsonPath("$.projectionCone.numScenarios").value(100000))
                .andExpect(jsonPath("$.projectionCone.var99.amount").value(-5000.00))
                .andExpect(jsonPath("$.projectionCone.medianP50.amount").value(78000.00))
                .andExpect(jsonPath("$.projectionCone.ruinProbabilityPct.value").value(6.5))
                .andExpect(jsonPath("$.projectionCone.dailyBands[0].dayOffset").value(1));

        verify(getUncertaintyConeUseCase).execute(30, simulationId);
    }

    @Test
    void shouldExportCsvReportViaGetWithFilters() throws Exception {
        ReportExportFilter expectedFilter = new ReportExportFilter(
                "PAYABLE",
                "OVERDUE",
                null,
                null,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                "Fornecedor"
        );
        ReportRowItem row = new ReportRowItem(
                UUID.fromString("0192c8a4-0000-7000-8000-000000000777"),
                "PAYABLE",
                "OVERDUE",
                "Fornecedor Energia",
                "Utilidades",
                "Conta Principal",
                "Companhia Eletrica",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 12),
                null,
                new BigDecimal("3400.00"),
                null,
                "BRL"
        );

        when(exportEntriesReportUseCase.fetchRows(eq(expectedFilter))).thenReturn(List.of(row));

        MvcResult result = mockMvc.perform(get("/v1/reports/entries/export")
                        .param("format", "CSV")
                        .param("entryType", "PAYABLE")
                        .param("status", "OVERDUE")
                        .param("dueDateFrom", "2026-10-01")
                        .param("dueDateTo", "2026-10-31")
                        .param("search", "Fornecedor"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("lancamentos-financeiros.csv")))
                .andReturn();

        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body)
                .startsWith(CsvStreamingExporter.UTF8_BOM)
                .contains("Fornecedor Energia;Utilidades;Conta Principal;Companhia Eletrica");
    }

    @Test
    void shouldExportPdfReportViaPostWithJsonPayload() throws Exception {
        ReportExportFilter expectedFilter = new ReportExportFilter(
                "RECEIVABLE",
                "PENDING",
                null,
                null,
                null,
                null,
                null
        );
        ReportRowItem row = new ReportRowItem(
                UUID.fromString("0192c8a4-0000-7000-8000-000000000888"),
                "RECEIVABLE",
                "PENDING",
                "Fatura SaaS Corporativo",
                "Receitas",
                "Conta Principal",
                "Tech Corp",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 28),
                null,
                new BigDecimal("15000.00"),
                null,
                "BRL"
        );

        when(exportEntriesReportUseCase.fetchRows(eq(expectedFilter))).thenReturn(List.of(row));

        MvcResult result = mockMvc.perform(post("/v1/reports/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "format": "PDF",
                                  "entryType": "RECEIVABLE",
                                  "status": "PENDING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("relatorio-financeiro.pdf")))
                .andReturn();

        String pdfContent = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1);
        assertThat(pdfContent)
                .startsWith("%PDF-1.4")
                .contains("Fatura SaaS Corporativo")
                .endsWith("%%EOF\n");
    }
}
