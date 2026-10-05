package com.nimokids.util;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Request-scoped constants and helpers shared by the API envelope, logging and filters. */
public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String ANONYMOUS_ID_HEADER = "X-Anonymous-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    /** Request attributes that carry the error outcome from the exception handlers to the API logging filter. */
    public static final String ERROR_CODE_ATTRIBUTE = RequestContext.class.getName() + ".errorCode";
    public static final String ERROR_MESSAGE_ATTRIBUTE = RequestContext.class.getName() + ".errorMessage";

    private RequestContext() {
    }

    /** Returns the current correlation id from MDC, or a fresh UUID when no request filter has set one. */
    public static String currentRequestId() {
        String requestId = MDC.get(REQUEST_ID_MDC_KEY);
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }

    /** Remembers the error of the current request so that the API log can store it. No-op outside a request. */
    public static void recordError(String code, String message) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(ERROR_CODE_ATTRIBUTE, code, RequestAttributes.SCOPE_REQUEST);
            attributes.setAttribute(ERROR_MESSAGE_ATTRIBUTE, message, RequestAttributes.SCOPE_REQUEST);
        }
    }
}
