package com.nimokids.exception;

import com.nimokids.dto.response.ApiResponse;
import com.nimokids.dto.response.FieldErrorDetail;
import com.nimokids.util.RequestContext;
import jakarta.servlet.ServletException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.ErrorResponse;

/**
 * Single place that turns every failure into the standard error envelope.
 * Never exposes stack traces, SQL errors, Hibernate messages or internal paths.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("Business error {}: {}", code, ex.getMessage());
        return build(code, ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Validation failed", details);
    }

    /** Validation of @RequestParam / @PathVariable / header values (Spring 6.1+ method validation). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDetail> details = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Validation failed", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> details = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::toDetail)
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Validation failed", details);
    }

    /** Malformed JSON, invalid UUID or unknown enum value in the body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR, "Malformed request body", null);
    }

    /** Invalid UUID / enum in a path variable or query parameter. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        List<FieldErrorDetail> details = List.of(new FieldErrorDetail(ex.getName(), "invalid value"));
        return build(ErrorCode.VALIDATION_ERROR, "Validation failed", details);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        if (RequestContext.ANONYMOUS_ID_HEADER.equalsIgnoreCase(ex.getHeaderName())) {
            return build(ErrorCode.ANONYMOUS_PLAYER_REQUIRED, ErrorCode.ANONYMOUS_PLAYER_REQUIRED.getDefaultMessage(), null);
        }
        List<FieldErrorDetail> details = List.of(new FieldErrorDetail(ex.getHeaderName(), "header is required"));
        return build(ErrorCode.VALIDATION_ERROR, "Validation failed", details);
    }

    /** Spring Security exceptions raised below the filter chain must keep their 401/403 meaning. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getDefaultMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage(), null);
    }

    /** A second request that lost a concurrent race on the same answer (master 5.8, 5.9). */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleConcurrency(Exception ex) {
        log.warn("Concurrency conflict: {}", ex.getClass().getSimpleName());
        return build(ErrorCode.DUPLICATE_ANSWER, ErrorCode.DUPLICATE_ANSWER.getDefaultMessage(), null);
    }

    /** Spring MVC protocol errors (404 route, 405, 415, missing parameter, ...). */
    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ApiResponse<Void>> handleServlet(ServletException ex) {
        HttpStatusCode status = ex instanceof ErrorResponse errorResponse
                ? errorResponse.getStatusCode()
                : HttpStatus.BAD_REQUEST;
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage(), null);
        }
        if (status.is5xxServerError()) {
            return handleUnexpected(ex);
        }
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Invalid request", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(ErrorCode.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
    }

    private static FieldErrorDetail toDetail(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        return new FieldErrorDetail(field, violation.getMessage());
    }

    private static ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message, Object details) {
        RequestContext.recordError(code.name(), message);
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.error(code, message, details));
    }
}
