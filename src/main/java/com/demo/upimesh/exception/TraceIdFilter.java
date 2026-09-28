package com.demo.upimesh.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String TRACE_ID = "traceId";

    public static String traceId(HttpServletRequest request) {
        Object existing = request.getAttribute(TRACE_ID);
        if (existing instanceof String id) return id;
        String id = request.getHeader("X-Trace-Id");
        if (id == null || !id.matches("[A-Za-z0-9._:-]{1,128}")) {
            id = UUID.randomUUID().toString();
        }
        request.setAttribute(TRACE_ID, id);
        return id;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String previous = MDC.get(TRACE_ID);
        String id = traceId(request);
        MDC.put(TRACE_ID, id);
        response.setHeader("X-Trace-Id", id);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) MDC.remove(TRACE_ID);
            else MDC.put(TRACE_ID, previous);
        }
    }
}

