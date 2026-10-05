package com.nimokids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nimokids.exception.ErrorCode;
import com.nimokids.util.RequestContext;
import java.time.Instant;

/**
 * Standard response envelope (project_master_context.md sections 6.3 - 6.4).
 * "error" is only present on failures; "data" is always present (null on failures).
 */
public record ApiResponse<T>(
        Status status,
        String message,
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) ApiError error,
        String requestId,
        Instant timestamp) {

    public enum Status {
        SUCCESS,
        ERROR
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(Status.SUCCESS, message, data, null, RequestContext.currentRequestId(), Instant.now());
    }

    public static ApiResponse<Void> error(ErrorCode code, String message, Object details) {
        return new ApiResponse<>(
                Status.ERROR,
                message,
                null,
                new ApiError(code.name(), details),
                RequestContext.currentRequestId(),
                Instant.now());
    }
}
