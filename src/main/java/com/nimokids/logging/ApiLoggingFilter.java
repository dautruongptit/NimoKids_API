package com.nimokids.logging;

import com.nimokids.service.ApiLogService;
import com.nimokids.util.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Records one technical log line per API request (method, route, status, processing time, ...).
 * It is a servlet filter rather than an interceptor so that it also sees requests rejected before reaching
 * a controller (401, 403, 404, 405). Runs right after the request-id filter and wraps everything else.
 * The row is written asynchronously by {@link ApiLogService}; this class only gathers data.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiLoggingFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";
    private static final String CATCH_ALL_PATTERN = "/**";

    private final ApiLogService apiLogService;

    /** Header holding the real client IP behind a proxy (e.g. CF-Connecting-IP). Empty = use the socket address. */
    private final String clientIpHeader;

    public ApiLoggingFilter(ApiLogService apiLogService,
                            @Value("${app.api-log.client-ip-header:}") String clientIpHeader) {
        this.apiLogService = apiLogService;
        this.clientIpHeader = clientIpHeader == null ? "" : clientIpHeader.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        boolean failed = false;
        try {
            chain.doFilter(request, wrapped);
        } catch (IOException | ServletException | RuntimeException ex) {
            failed = true;
            throw ex;
        } finally {
            int responseSize = wrapped.getContentSize();
            int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : wrapped.getStatus();
            wrapped.copyBodyToResponse();
            publish(request, status, responseSize, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        }
    }

    private void publish(HttpServletRequest request, int status, int responseSize, long elapsedMs) {
        try {
            UUID requestId = parseUuid(MDC.get(RequestContext.REQUEST_ID_MDC_KEY));
            if (requestId == null) {
                return;
            }
            apiLogService.record(new ApiLogEvent(
                    requestId,
                    request.getMethod(),
                    endpointOf(request),
                    status,
                    elapsedMs,
                    clientIp(request),
                    request.getHeader("User-Agent"),
                    request.getContentLengthLong() >= 0 ? (int) Math.min(Integer.MAX_VALUE, request.getContentLengthLong()) : null,
                    responseSize,
                    (String) request.getAttribute(RequestContext.ERROR_CODE_ATTRIBUTE),
                    (String) request.getAttribute(RequestContext.ERROR_MESSAGE_ATTRIBUTE),
                    parseUuid(request.getHeader(RequestContext.ANONYMOUS_ID_HEADER)),
                    sessionIdFromPath(request)));
        } catch (RuntimeException ex) {
            // Includes a full async queue: a dropped log line must never affect the response.
            log.debug("API log dropped: {}", ex.getClass().getSimpleName());
        }
    }

    /** The matched route template groups logs per endpoint; raw URI (no query string) only for unmatched routes. */
    private static String endpointOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        // "/**" is Spring's catch-all resource handler: it means no controller matched, so keep the real path.
        return pattern != null && !CATCH_ALL_PATTERN.equals(pattern.toString())
                ? pattern.toString()
                : request.getRequestURI();
    }

    @SuppressWarnings("unchecked")
    private static UUID sessionIdFromPath(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables instanceof Map<?, ?> map ? parseUuid(((Map<String, String>) map).get("sessionId")) : null;
    }

    private String clientIp(HttpServletRequest request) {
        if (!clientIpHeader.isEmpty()) {
            String forwarded = request.getHeader(clientIpHeader);
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
