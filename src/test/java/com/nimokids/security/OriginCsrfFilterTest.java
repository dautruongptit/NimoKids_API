package com.nimokids.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OriginCsrfFilterTest {

    private final OriginCsrfFilter filter = new OriginCsrfFilter(List.of("https://nimokids.example/", "http://localhost:5100"),
            new ObjectMapper().registerModule(new JavaTimeModule()));

    private MockHttpServletResponse run(String method, String uri, String origin, String custom, String host) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setScheme("https");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        if (custom != null) {
            request.addHeader(OriginCsrfFilter.HEADER, custom);
        }
        if (host != null) {
            request.addHeader("Host", host);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        chainPassed = response.getStatus() == 200 && !response.isCommitted() && response.getContentLength() == 0;
        return response;
    }

    private boolean chainPassed;

    @Test
    void anAllowedOriginWithTheCustomHeaderPasses() throws Exception {
        assertThat(run("POST", "/api/v1/auth/refresh", "https://nimokids.example", "web", null).getStatus()).isEqualTo(200);
        assertThat(run("POST", "/api/v1/auth/logout", "http://localhost:5100", "web", null).getStatus()).isEqualTo(200);
    }

    @Test
    void theSitesOwnOriginPassesEvenWhenItIsNotListed() throws Exception {
        assertThat(run("POST", "/api/v1/auth/refresh", "https://site.example", "web", "site.example").getStatus()).isEqualTo(200);
    }

    @Test
    void aForeignOriginIsRejected() throws Exception {
        MockHttpServletResponse response = run("POST", "/api/v1/auth/refresh", "https://evil.example", "web", "site.example");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_REJECTED");
    }

    @Test
    void aMissingOriginOrCustomHeaderIsRejected() throws Exception {
        assertThat(run("POST", "/api/v1/auth/refresh", null, "web", null).getStatus()).isEqualTo(403);
        assertThat(run("POST", "/api/v1/auth/refresh", "https://nimokids.example", null, null).getStatus()).isEqualTo(403);
        assertThat(run("POST", "/api/v1/auth/refresh", "https://nimokids.example", "other", null).getStatus()).isEqualTo(403);
    }

    @Test
    void otherEndpointsAndMethodsAreNotTouched() throws Exception {
        assertThat(run("POST", "/api/v1/auth/login", null, null, null).getStatus()).isEqualTo(200);
        assertThat(run("GET", "/api/v1/auth/session", null, null, null).getStatus()).isEqualTo(200);
        assertThat(run("GET", "/api/v1/auth/refresh", null, null, null).getStatus()).isEqualTo(200);
        assertThat(run("POST", "/api/v1/game-sessions", null, null, null).getStatus()).isEqualTo(200);
    }
}
