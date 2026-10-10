package com.nimokids.security;

import com.nimokids.exception.ErrorCode;
import com.nimokids.service.auth.SessionGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates "Authorization: Bearer &lt;JWT&gt;" for the Admin area (/api/v1/admin/**) and the account endpoints
 * (/api/v1/auth/**). Player endpoints never parse a token: they are anonymous.
 *
 * A valid token becomes an {@link AdminPrincipal} with the authorities of the {@link AuthorityResolver}. A token bound
 * to a session (claim sid) is additionally checked against the session store on every request, so a revoked or expired
 * session stops working at once even though the JWT itself is still valid. A refused token leaves the request
 * unauthenticated and records the reason (TOKEN_EXPIRED, TOKEN_INVALID, SESSION_EXPIRED, SESSION_REVOKED) for the
 * 401 answer; an end-user token on an admin path is refused as FORBIDDEN.
 *
 * Tokens issued before sessions existed (no sid) are still accepted until they expire.
 * Deliberately not a Spring bean, so it is registered once, inside the security filter chain only.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthorityResolver authorityResolver;
    private final SessionGuard sessionGuard;

    public JwtAuthenticationFilter(JwtService jwtService, AuthorityResolver authorityResolver) {
        this(jwtService, authorityResolver, null);
    }

    public JwtAuthenticationFilter(JwtService jwtService, AuthorityResolver authorityResolver, SessionGuard sessionGuard) {
        this.jwtService = jwtService;
        this.authorityResolver = authorityResolver;
        this.sessionGuard = sessionGuard;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (SecurityPaths.isAdminPath(request)) {
            return false;
        }
        // Login, refresh and the session probe never read a Bearer token (refresh is authenticated by its cookie).
        String path = request.getRequestURI();
        boolean tokenFree = path.equals(SecurityPaths.LOGIN_PATH) || path.equals(SecurityPaths.REFRESH_PATH)
                || path.equals(SecurityPaths.SESSION_PATH) || path.startsWith(SecurityPaths.AUTH_BASE + "/google/");
        return !SecurityPaths.isAuthPath(request) || tokenFree;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null
                && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(header.substring(BEARER_PREFIX.length()).trim(), request);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        JwtService.ParseResult result = jwtService.parseDetailed(token);
        if (result.status() == JwtService.Status.EXPIRED) {
            fail(request, ErrorCode.TOKEN_EXPIRED);
            return;
        }
        if (result.status() != JwtService.Status.VALID || result.principal().isEmpty()) {
            fail(request, ErrorCode.TOKEN_INVALID);
            return;
        }
        JwtPrincipal claims = result.principal().get();
        if (SecurityPaths.isAdminPath(request) && "USER".equals(claims.type())) {
            fail(request, ErrorCode.FORBIDDEN);   // an end-user token never opens the admin area
            return;
        }
        if (sessionGuard != null && claims.sessionId() != null) {
            SessionGuard.Result session = sessionGuard.check(claims.sessionId(), request);
            if (session == SessionGuard.Result.EXPIRED) {
                fail(request, ErrorCode.SESSION_EXPIRED);
                return;
            }
            if (session == SessionGuard.Result.REVOKED) {
                fail(request, ErrorCode.SESSION_REVOKED);
                return;
            }
        }
        AdminPrincipal principal = new AdminPrincipal(claims.subject(), claims.role(), claims.type(), claims.sessionId());
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, authorityResolver.resolve(principal));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void fail(HttpServletRequest request, ErrorCode code) {
        request.setAttribute(AuthFailure.ATTRIBUTE, code);
    }
}
