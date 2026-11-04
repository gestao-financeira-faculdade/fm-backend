package com.emanudev.gestaofinanceira.shared.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationMdcFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_CORRELATION_ID = "correlation_id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_HTTP_METHOD = "http_method";
    public static final String MDC_HTTP_PATH = "http_path";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String correlationId = resolveCorrelationId(request);
            MDC.put(MDC_CORRELATION_ID, correlationId);
            if (MDC.get(MDC_TRACE_ID) == null) {
                MDC.put(MDC_TRACE_ID, correlationId);
            }
            if (request.getMethod() != null) {
                MDC.put(MDC_HTTP_METHOD, request.getMethod());
            }
            if (request.getRequestURI() != null) {
                MDC.put(MDC_HTTP_PATH, request.getRequestURI());
            }
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String header = request.getHeader(CORRELATION_ID_HEADER);
        if (header != null && !header.isBlank() && header.trim().length() <= 64) {
            return header.trim();
        }
        return UUID.randomUUID().toString();
    }
}
