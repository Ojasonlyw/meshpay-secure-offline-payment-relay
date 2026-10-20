package com.demo.upimesh;

import com.demo.upimesh.exception.RequestSizeFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestSizeFilterTest {
    private final RequestSizeFilter filter = new RequestSizeFilter(new ObjectMapper().findAndRegisterModules(), 32);

    @Test void declaredOversizeNeverReadsOrCallsTheApplication() throws Exception {
        var request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return 33; }
            @Override public jakarta.servlet.ServletInputStream getInputStream() { throw new AssertionError("body must not be read"); }
        };
        var chain = mock(FilterChain.class);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(413, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test void chunkedAndMisleadingLengthStillCannotExceedTheCap() throws Exception {
        for (long declared : new long[]{-1, 1}) {
            var request = new MockHttpServletRequest() {
                @Override public long getContentLengthLong() { return declared; }
            };
            request.setContent(new byte[33]);
            var chain = mock(FilterChain.class);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(413, response.getStatus());
            verifyNoInteractions(chain);
        }
    }
}
