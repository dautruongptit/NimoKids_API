package com.nimokids.service.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The refresh-token cookie: HttpOnly, Secure, SameSite=Strict, host-only, path limited to /api/v1/auth. JavaScript
 * never sees it, and the browser sends it to the auth endpoints only.
 */
@Component
@RequiredArgsConstructor
public class AuthCookies {

    public static final String PATH = "/api/v1/auth";

    private final AuthProperties properties;
    private final Clock clock;

    public void setRefresh(HttpServletResponse response, String rawToken, Instant expiresAt) {
        long seconds = Math.max(0, Duration.between(clock.instant(), expiresAt).getSeconds());
        response.addHeader("Set-Cookie", build(rawToken, seconds));
    }

    public void clearRefresh(HttpServletResponse response) {
        response.addHeader("Set-Cookie", build("", 0));
    }

    public String readRefresh(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (properties.refreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String build(String value, long maxAgeSeconds) {
        return properties.refreshCookieName() + "=" + value + "; Max-Age=" + maxAgeSeconds + "; Path=" + PATH
                + "; HttpOnly; SameSite=Strict" + (properties.cookieSecure() ? "; Secure" : "");
    }
}
