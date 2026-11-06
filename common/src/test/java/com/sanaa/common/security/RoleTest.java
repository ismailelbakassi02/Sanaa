package com.sanaa.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void everyRoleHasTheSpringAuthorityPrefix() {
        assertThat(Role.values())
                .extracting(Role::authority)
                .containsExactly(
                        "ROLE_SUPER_ADMIN",
                        "ROLE_TENANT_ADMIN",
                        "ROLE_INSTRUCTOR",
                        "ROLE_STUDENT");
    }
}
