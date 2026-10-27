package com.emanudev.gestaofinanceira.audit.application;

import java.util.ArrayDeque;
import java.util.Deque;

public final class AuditExecutionContext {

    private static final ThreadLocal<Deque<Boolean>> EXPLICIT_AUDIT_STACK = new ThreadLocal<>();

    private AuditExecutionContext() {
    }

    public static void beginScope() {
        Deque<Boolean> stack = EXPLICIT_AUDIT_STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            EXPLICIT_AUDIT_STACK.set(stack);
        }
        stack.push(Boolean.FALSE);
    }

    public static void markExplicitAuditRecorded() {
        Deque<Boolean> stack = EXPLICIT_AUDIT_STACK.get();
        if (stack != null && !stack.isEmpty()) {
            stack.pollFirst();
            stack.push(Boolean.TRUE);
        }
    }

    public static boolean isExplicitAuditRecorded() {
        Deque<Boolean> stack = EXPLICIT_AUDIT_STACK.get();
        return stack != null && Boolean.TRUE.equals(stack.peekFirst());
    }

    public static void endScope() {
        Deque<Boolean> currentStack = EXPLICIT_AUDIT_STACK.get();
        if (currentStack != null) {
            currentStack.pollFirst();
            if (currentStack.isEmpty()) {
                EXPLICIT_AUDIT_STACK.remove();
            }
        }
    }
}
