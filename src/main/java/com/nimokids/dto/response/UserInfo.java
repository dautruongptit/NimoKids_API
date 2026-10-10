package com.nimokids.dto.response;

/** Who is signed in. No token, no secret. {@code type} is ADMIN or USER. */
public record UserInfo(String id, String type, String role, String email, String displayName, String avatarUrl) {
}
