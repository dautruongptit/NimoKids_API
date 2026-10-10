package com.nimokids.config;

import com.nimokids.service.auth.google.GoogleOidcProperties;
import com.nimokids.service.auth.SessionGuard;
import com.nimokids.service.auth.AuthProperties;
import com.nimokids.security.OriginCsrfFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimokids.security.AuthorityResolver;
import com.nimokids.security.JwtAuthenticationFilter;
import com.nimokids.security.JwtProperties;
import com.nimokids.security.JwtService;
import com.nimokids.security.SecurityErrorHandler;
import com.nimokids.security.SecurityPaths;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Hybrid authentication:
 * <ul>
 *   <li>Players are anonymous. Their endpoints are an explicit allow-list (see {@link SecurityPaths}); they never
 *       need a JWT and X-Anonymous-Id is only an identifier, NOT authentication.</li>
 *   <li>POST /api/v1/auth/login is open so an admin can obtain a JWT.</li>
 *   <li>/api/v1/admin/** requires an authenticated admin (valid JWT): 401 without or with an invalid token.
 *       WHAT an admin may do is decided per endpoint with {@code @PreAuthorize} (403 when the role is not enough),
 *       so controllers keep working unchanged when roles become dynamic RBAC.</li>
 *   <li>Anything not listed is denied, so a new endpoint is never public by accident.</li>
 * </ul>
 * Rules are evaluated top to bottom; the first match wins.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // turns on @PreAuthorize / @PostAuthorize
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class, GoogleOidcProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, AuthorityResolver authorityResolver, SecurityErrorHandler errorHandler,
            SessionGuard sessionGuard, AuthProperties authProperties, ObjectMapper objectMapper)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless API: no cookies or sessions to protect
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()          // CORS preflight
                        .requestMatchers(SecurityPaths.ADMIN_PATTERN).authenticated()
                        .requestMatchers(HttpMethod.POST, SecurityPaths.LOGIN_PATH).permitAll()
                        // Cookie-authenticated (CSRF-checked) or self-checking endpoints of the session system:
                        .requestMatchers(HttpMethod.POST, SecurityPaths.REFRESH_PATH, SecurityPaths.LOGOUT_PATH).permitAll()
                        .requestMatchers(HttpMethod.GET, SecurityPaths.SESSION_PATH).permitAll()
                        .requestMatchers(HttpMethod.GET, SecurityPaths.AUTH_BASE + "/google/start", SecurityPaths.AUTH_BASE + "/google/callback").permitAll()
                        .requestMatchers(SecurityPaths.AUTH_BASE + "/**").authenticated()   // me, logout-all, sessions
                        .requestMatchers(SecurityPaths.PUBLIC_PATTERNS).permitAll()
                        .requestMatchers("/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(new OriginCsrfFilter(authProperties.allowedOrigins(), objectMapper), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, authorityResolver, sessionGuard), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins.stream().filter(origin -> !origin.isBlank()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Content-Type", "Authorization", "X-Anonymous-Id", "X-Request-Id", "X-NK-Requested-With"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Login is handled by AuthService, not by Spring's form login, so there is no UserDetailsService to offer.
     * Declaring one stops Spring Boot from generating a default user with a random password.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Authentication is handled by AuthService");
        };
    }
}
