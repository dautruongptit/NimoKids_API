package com.nimokids.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/auth/login. Used by the Admin portal only; players never log in. */
public record LoginRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        @Size(max = 254, message = "must be at most 254 characters") String email,
        // bcrypt only uses the first 72 bytes, so longer passwords are rejected instead of silently truncated.
        @NotBlank(message = "must not be blank") @Size(max = 72, message = "must be at most 72 characters") String password) {

    /** Never print the password, even by accident. */
    @Override
    public String toString() {
        return "LoginRequest(email=" + email + ", password=***)";
    }
}
