package com.nimokids.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** MVP implementation: one role per admin, taken from the JWT, and exposed as the authority ROLE_&lt;role&gt;. */
@Component
public class StaticRoleAuthorityResolver implements AuthorityResolver {

    /** Spring's hasRole('X') checks for the authority "ROLE_X". */
    static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<? extends GrantedAuthority> resolve(AdminPrincipal principal) {
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + principal.role()));
    }
}
