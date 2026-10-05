package com.nimokids.dto.response;

import com.nimokids.entity.enums.UserRole;

/** The JWT carries the same role in its "role" claim. Send it as "Authorization: Bearer &lt;accessToken&gt;". */
public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, String username, UserRole role) {
}
