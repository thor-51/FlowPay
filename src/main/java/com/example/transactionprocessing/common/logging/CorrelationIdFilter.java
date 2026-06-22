package com.example.transactionprocessing.common.logging;

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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Not part of the original brief, added alongside the JSON logging setup: every log line
 * emitted while handling a request gets a shared correlationId in its MDC, which
 * logback-spring.xml's JSON encoder picks up automatically. Without this, correlating "which
 * log lines belong to the same HTTP request" in Kibana means grepping by timestamp proximity —
 * with it, it's a single field filter. Honors an inbound X-Correlation-Id header so a caller (or
 * an upstream gateway) can supply its own trace id and have it carried through.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // MDC is thread-local and this app runs on a (potentially reused) servlet container
            // thread pool, so this MUST be cleared in a finally block — otherwise a later
            // request handled by the same thread could inherit a stale correlationId.
            MDC.remove(MDC_KEY);
        }
    }
}
