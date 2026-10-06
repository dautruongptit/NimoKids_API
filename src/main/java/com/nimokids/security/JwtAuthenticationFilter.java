package com.nimokids.security;

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
 * Authenticates "Authorization: Bearer &lt;JWT&gt;" for the Admin area (/api/v1/admin/**) only. Player endpoints
 * never parse a token: they are anonymous.
 *
 * When the token is valid, the role from its payload is turned into Spring authorities by the
 * {@link AuthorityResolver} (ROLE_&lt;role&gt; today) and stored in the SecurityContext. An invalid or missing token
 * leaves the request unauthenticated and the authorization rules answer 401; a valid token that lacks the role
 * demanded by @PreAuthorize gets 403.
 *
 * If the URL is not recognised as an admin path here, the request simply stays unauthenticated, so a mismatch can
 * only deny access, never grant it. Deliberately not a Spring bean, so it is registered once, inside the security
 * filter chain only.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthorityResolver authorityResolver;

    public JwtAuthenticationFilter(JwtService jwtService, AuthorityResolver authorityResolver) {
        this.jwtService = jwtService;
        this.authorityResolver = authorityResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !SecurityPaths.isAdminPath(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null
                && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            jwtService.parse(token).ifPresent(claims -> {
                AdminPrincipal principal = new AdminPrincipal(claims.subject(), claims.role());
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, authorityResolver.resolve(principal));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(request, response);
    }
}
