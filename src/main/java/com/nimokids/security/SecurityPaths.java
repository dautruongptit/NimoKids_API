package com.nimokids.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The URL map of the hybrid security model, in one place so SecurityConfig and JwtAuthenticationFilter agree.
 *
 * <pre>
 *   public (no JWT; players are identified by X-Anonymous-Id, which is NOT authentication)
 *   /api/v1/admin/**   JWT required AND role ADMIN
 *   everything else    denied
 * </pre>
 */
public final class SecurityPaths {

    public static final String ADMIN_BASE = "/api/v1/admin";
    public static final String ADMIN_PATTERN = ADMIN_BASE + "/**";

    public static final String AUTH_BASE = "/api/v1/auth";
    public static final String LOGIN_PATH = AUTH_BASE + "/login";
    public static final String REFRESH_PATH = AUTH_BASE + "/refresh";
    public static final String LOGOUT_PATH = AUTH_BASE + "/logout";
    public static final String SESSION_PATH = AUTH_BASE + "/session";

    /**
     * Endpoints open to everyone. "/api/v1/game/**" is the path used in project-context.md and
     * "/api/v1/game-sessions/**" the one implemented (project_master_context.md); both are allowed.
     */
    public static final String[] PUBLIC_PATTERNS = {
            "/api/v1/game/**",
            "/api/v1/game-sessions/**",
            "/api/v1/topics/**",
            "/api/v1/game-modes/**",
            "/api/v1/media/**",
            "/api/v1/activities/**",
            "/api/v1/players/**",
    };

    private SecurityPaths() {
    }

    public static boolean isAuthPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals(AUTH_BASE) || path.startsWith(AUTH_BASE + "/");
    }

    public static boolean isAdminPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals(ADMIN_BASE) || path.startsWith(ADMIN_BASE + "/");
    }
}
