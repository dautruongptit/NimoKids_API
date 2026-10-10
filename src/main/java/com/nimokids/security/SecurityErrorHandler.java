package com.nimokids.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.dto.response.ApiResponse;
import com.nimokids.exception.ErrorCode;
import com.nimokids.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Writes 401 / 403 in the standard error envelope, because these happen before any controller advice runs. */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        // The filter says why the token was refused (expired, revoked ...); no token at all is a plain 401.
        Object reason = request.getAttribute(AuthFailure.ATTRIBUTE);
        ErrorCode code = reason instanceof ErrorCode known ? known : ErrorCode.UNAUTHORIZED;
        if (code.getHttpStatus().value() == 401) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        write(response, code);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        RequestContext.recordError(code.name(), code.getDefaultMessage());
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, code.getDefaultMessage(), null));
    }
}
