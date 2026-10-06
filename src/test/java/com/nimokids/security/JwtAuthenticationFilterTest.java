package com.nimokids.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("unit-test-secret-key-with-at-least-32-bytes!".getBytes(StandardCharsets.UTF_8));

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, new StaticRoleAuthorityResolver());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminRequestWithValidTokenIsAuthenticatedWithTheRoleFromTheToken() throws Exception {
        Authentication authentication = run("/api/v1/admin/topics", "Bearer " + jwtService.generateToken("admin-1", "SUPER_ADMIN"));

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new AdminPrincipal("admin-1", "SUPER_ADMIN"));
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_SUPER_ADMIN");
    }

    @Test
    void theBaseAdminPathIsAlsoProcessed() throws Exception {
        assertThat(run("/api/v1/admin", "Bearer " + jwtService.generateToken("admin-1", "ADMIN"))).isNotNull();
    }

    @Test
    void playerEndpointsNeverParseATokenEvenIfOneIsSent() throws Exception {
        String token = "Bearer " + jwtService.generateToken("admin-1", "ADMIN");

        assertThat(run("/api/v1/topics", token)).isNull();
        assertThat(run("/api/v1/game-sessions/abc/submit-answer", token)).isNull();
        assertThat(run("/api/v1/administrator", token)).isNull();
        assertThat(run("/api/v1/auth/login", token)).isNull();
    }

    @Test
    void badOrMissingTokensLeaveTheAdminRequestUnauthenticated() throws Exception {
        assertThat(run("/api/v1/admin/topics", null)).isNull();
        assertThat(run("/api/v1/admin/topics", "Bearer garbage")).isNull();
        assertThat(run("/api/v1/admin/topics", "Bearer " + jwtService.generateToken("admin-1", "ADMIN", Duration.ofSeconds(-1)))).isNull();
        assertThat(run("/api/v1/admin/topics", "Basic YWRtaW46YWRtaW4=")).isNull();
    }

    @Test
    void theRequestAlwaysContinuesDownTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/topics");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private Authentication run(String uri, String authorization) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
