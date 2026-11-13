package com.emanudev.gestaofinanceira.audit.adapter.in.aop;

import com.emanudev.gestaofinanceira.audit.application.AuditExecutionContext;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.application.AuditedFinancialAction;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
public class FinancialAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(FinancialAuditAspect.class);

    private final AuditTrailPort auditTrailPort;

    public FinancialAuditAspect(AuditTrailPort auditTrailPort) {
        this.auditTrailPort = auditTrailPort;
    }

    public static void markExplicitAuditRecorded() {
        AuditExecutionContext.markExplicitAuditRecorded();
    }

    @Around("@annotation(auditedAction)")
    public Object interceptAuditedAction(
            ProceedingJoinPoint joinPoint,
            AuditedFinancialAction auditedAction
    ) throws Throwable {
        AuditExecutionContext.beginScope();

        try {
            Object result = joinPoint.proceed();
            if (!AuditExecutionContext.isExplicitAuditRecorded()) {
                recordFallbackAudit(joinPoint, auditedAction, result);
            }
            return result;
        } finally {
            AuditExecutionContext.endScope();
        }
    }

    private void recordFallbackAudit(
            ProceedingJoinPoint joinPoint,
            AuditedFinancialAction auditedAction,
            Object result
    ) {
        AuditAction action = auditedAction.action();
        String entityType = auditedAction.entityType();
        Object[] args = joinPoint.getArgs();

        UUID entityId = resolveEntityId(result, args);
        Object previousState = action == AuditAction.CREATE ? null : resolvePreviousState(args);
        Object newState = result != null ? result : Map.of("action", action.name(), "entityId", entityId.toString());

        auditTrailPort.recordTransition(entityType, entityId, action, previousState, newState);

        log.atDebug()
                .addKeyValue("event_type", "AOP_AUDIT_INTERCEPTED")
                .addKeyValue("entity_type", entityType)
                .addKeyValue("entity_id", entityId)
                .addKeyValue("audit_action", action.name())
                .log("Audited financial action intercepted by AOP");
    }

    private Object resolvePreviousState(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        return args.length == 1 ? args[0] : args;
    }

    private UUID resolveEntityId(Object result, Object[] args) {
        UUID fromResult = extractUuid(result);
        if (fromResult != null) {
            return fromResult;
        }
        if (args != null) {
            for (Object arg : args) {
                UUID fromArg = extractUuid(arg);
                if (fromArg != null) {
                    return fromArg;
                }
            }
        }
        return UUID.randomUUID();
    }

    private UUID extractUuid(Object candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate instanceof UUID uuid) {
            return uuid;
        }
        for (String accessor : new String[]{"id", "getId", "entryId", "getEntryId", "entityId", "getEntityId"}) {
            try {
                Method method = candidate.getClass().getMethod(accessor);
                Object value = method.invoke(candidate);
                if (value instanceof UUID uuid) {
                    return uuid;
                }
            } catch (ReflectiveOperationException ignored) {
                // ignore and try next accessor
            }
        }
        return null;
    }
}
