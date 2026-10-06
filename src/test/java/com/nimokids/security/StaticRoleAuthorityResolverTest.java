package com.nimokids.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StaticRoleAuthorityResolverTest {

    private final AuthorityResolver resolver = new StaticRoleAuthorityResolver();

    @Test
    void theRoleBecomesASingleRolePrefixedAuthority() {
        assertThat(resolver.resolve(new AdminPrincipal("1", "SUPER_ADMIN")))
                .extracting(Object::toString).containsExactly("ROLE_SUPER_ADMIN");
        assertThat(resolver.resolve(new AdminPrincipal("2", "ADMIN")))
                .extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void thereIsNoImplicitHierarchyBetweenRoles() {
        assertThat(resolver.resolve(new AdminPrincipal("1", "SUPER_ADMIN")))
                .extracting(Object::toString).doesNotContain("ROLE_ADMIN");
    }
}
