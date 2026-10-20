package io.github.sayamrina.solutiondesk.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a short ID, puts it into the log context and the response header, and logs
 * method, path, status and duration. Slow requests are logged as SD-PERF-001 warnings.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final Logger log = LoggerFactory.getLogger("solutiondesk.access");

    private final long slowMs;

    public RequestIdFilter(@Value("${solutiondesk.slow-request-ms}") long slowMs) {
        this.slowMs = slowMs;
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/css/") || path.startsWith("/actuator") || path.equals("/favicon.ico")) {
            chain.doFilter(request, response);
            return;
        }
        String requestId = newRequestId();
        long start = System.nanoTime();
        MDC.put("requestId", requestId);
        MDC.put("method", request.getMethod());
        MDC.put("path", path);
        response.setHeader(HEADER, requestId);
        request.setAttribute("requestId", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            MDC.put("status", String.valueOf(response.getStatus()));
            MDC.put("durationMs", String.valueOf(ms));
            if (ms > slowMs) {
                MDC.put("errorCode", "SD-PERF-001");
                log.warn("slow request");
            } else {
                log.info("request completed");
            }
            MDC.clear();
        }
    }
}
