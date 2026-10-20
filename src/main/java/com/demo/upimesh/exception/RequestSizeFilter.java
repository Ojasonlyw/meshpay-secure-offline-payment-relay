package com.demo.upimesh.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds both declared and chunked request bodies before JSON parsing or cryptographic work. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    private final int maxBytes;

    public RequestSizeFilter(ObjectMapper json, @Value("${mesh.max-request-bytes:32768}") int maxBytes) {
        if (maxBytes < 1 || maxBytes > 1048576) throw new IllegalArgumentException("Invalid request limit");
        this.json = json;
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) { reject(request, response); return; }
        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) { reject(request, response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return body.length; }
            @Override public long getContentLengthLong() { return body.length; }
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous MVC only"); }
                };
            }
            @Override public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String traceId = TraceIdFilter.traceId(request);
        response.setStatus(413);
        response.setHeader("X-Trace-Id", traceId);
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), new ApiErrorResponse(Instant.now(), 413,
                "PAYLOAD_TOO_LARGE", "Request body exceeds the demo size limit.", request.getRequestURI(), traceId));
    }
}
