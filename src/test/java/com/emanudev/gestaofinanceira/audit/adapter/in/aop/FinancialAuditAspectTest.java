package com.emanudev.gestaofinanceira.audit.adapter.in.aop;

import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.application.AuditedFinancialAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FinancialAuditAspectTest {

    @Mock
    private AuditTrailPort auditTrailPort;

    private SampleAuditedService proxiedService;

    @BeforeEach
    void setUp() {
        FinancialAuditAspect aspect = new FinancialAuditAspect(auditTrailPort);
        SampleAuditedService target = new SampleAuditedService(auditTrailPort);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        proxiedService = factory.getProxy();
    }

    @Test
    void shouldAutomaticallyRecordAuditWhenUseCaseDoesNotCallAuditTrailPortDirectly() {
        UUID entryId = UUID.fromString("0192c8a4-0000-7000-8000-000000000010");

        SampleResult result = proxiedService.createWithoutExplicitAudit(entryId);

        assertThat(result.id()).isEqualTo(entryId);
        verify(auditTrailPort, times(1)).recordTransition(
                eq("FINANCIAL_ENTRY"),
                eq(entryId),
                eq(AuditAction.CREATE),
                isNull(),
                eq(result)
        );
    }

    @Test
    void shouldNotDuplicateAuditWhenUseCaseAlreadyRecordedExplicitTransition() {
        UUID entryId = UUID.fromString("0192c8a4-0000-7000-8000-000000000020");

        SampleResult result = proxiedService.settleWithExplicitRichAudit(entryId);

        assertThat(result.id()).isEqualTo(entryId);
        verify(auditTrailPort, times(1)).recordTransition(
                eq("FINANCIAL_ENTRY"),
                eq(entryId),
                eq(AuditAction.SETTLE),
                any(),
                any()
        );
    }

    public record SampleResult(UUID id, String status) {
    }

    public static class SampleAuditedService {

        private final AuditTrailPort auditTrailPort;

        public SampleAuditedService(AuditTrailPort auditTrailPort) {
            this.auditTrailPort = auditTrailPort;
        }

        @AuditedFinancialAction(action = AuditAction.CREATE, entityType = "FINANCIAL_ENTRY")
        public SampleResult createWithoutExplicitAudit(UUID id) {
            return new SampleResult(id, "PENDING");
        }

        @AuditedFinancialAction(action = AuditAction.SETTLE, entityType = "FINANCIAL_ENTRY")
        public SampleResult settleWithExplicitRichAudit(UUID id) {
            Map<String, Object> before = Map.of("id", id, "status", "PENDING");
            SampleResult after = new SampleResult(id, "SETTLED");
            FinancialAuditAspect.markExplicitAuditRecorded();
            auditTrailPort.recordTransition("FINANCIAL_ENTRY", id, AuditAction.SETTLE, before, after);
            return after;
        }
    }
}
