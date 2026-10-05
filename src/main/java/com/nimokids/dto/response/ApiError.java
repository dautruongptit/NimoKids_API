package com.nimokids.dto.response;

/**
 * Error block of the standard envelope.
 *
 * @param code    stable machine-readable code (see ErrorCode)
 * @param details null, or a list of {@link FieldErrorDetail} for validation errors
 */
public record ApiError(String code, Object details) {
}
