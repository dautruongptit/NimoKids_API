package com.nimokids.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.dto.response.ApiResponse;
import com.nimokids.exception.ErrorCode;
import com.nimokids.util.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF defence of the two endpoints that are authenticated by a cookie (POST /auth/refresh and POST /auth/logout).
 * Every other endpoint uses the Bearer header, which a cross-site page cannot add. A request must come from an allowed
 * origin (the configured list, or the site's own origin) AND carry {@code X-NK-Requested-With: web}, a header that
 * forces a CORS pre-flight for any other site. This is on top of the cookie's SameSite=Strict.
 */
public class OriginCsrfFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-NK-Requested-With";
    public static final String HEADER_VALUE = "web";

    private final List<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public OriginCsrfFilter(List<String> allowedOrigins, ObjectMapper objectMapper) {
        this.allowedOrigins = allowedOrigins.stream().map(o -> o.trim().replaceAll("/+$", "")).filter(o -> !o.isEmpty()).toList();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.equals(SecurityPaths.REFRESH_PATH) && !path.equals(SecurityPaths.LOGOUT_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean headerOk = HEADER_VALUE.equals(request.getHeader(HEADER));
        String origin = request.getHeader("Origin");
        if (headerOk && origin != null && (allowedOrigins.contains(origin) || isSameOrigin(request, origin))) {
            chain.doFilter(request, response);
            return;
        }
        ErrorCode code = ErrorCode.CSRF_REJECTED;
        RequestContext.recordError(code.name(), code.getDefaultMessage());
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, code.getDefaultMessage(), null));
    }

    private static boolean isSameOrigin(HttpServletRequest request, String origin) {
        String host = request.getHeader("Host");
        return host != null && origin.equals(request.getScheme() + "://" + host);
    }
}
