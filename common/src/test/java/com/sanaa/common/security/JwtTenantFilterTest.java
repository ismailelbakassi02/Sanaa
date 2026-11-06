package com.sanaa.common.security;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.sanaa.common.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTenantFilterTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";

    private final JwtTenantFilter filter = new JwtTenantFilter();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void setsTenantForAuthenticatedJwtAndClearsAfterwards() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim(JwtClaims.TENANT_ID, TENANT_A)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        FilterChain chain = (request, response) ->
                assertThat(TenantContext.get()).isEqualTo(TENANT_A);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void leavesTenantUnsetWhenThereIsNoJwtAuthentication() throws Exception {
        FilterChain chain = (request, response) -> assertThat(TenantContext.get()).isNull();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void clearsStaleTenantFromPooledThreadForUnauthenticatedRequest() throws Exception {
        TenantContext.set(TENANT_A);
        FilterChain chain = (request, response) -> assertThat(TenantContext.get()).isNull();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(TenantContext.get()).isNull();
    }
}
