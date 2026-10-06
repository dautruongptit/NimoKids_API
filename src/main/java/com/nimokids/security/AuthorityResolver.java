package com.nimokids.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/**
 * The single seam between "who is this admin" (a verified JWT) and "what may they do" (Spring authorities).
 * Controllers only declare rules such as {@code @PreAuthorize("hasRole('SUPER_ADMIN')")} or
 * {@code hasAuthority('topic:write')}; they never know how authorities are produced.
 *
 * Today: {@link StaticRoleAuthorityResolver} turns the token's role into ROLE_&lt;role&gt;.
 * Dynamic RBAC later: provide another implementation that loads the role's permissions from the database (and may
 * cache them), return ROLE_x plus permission authorities, and no controller or filter has to change.
 */
public interface AuthorityResolver {

    Collection<? extends GrantedAuthority> resolve(AdminPrincipal principal);
}
