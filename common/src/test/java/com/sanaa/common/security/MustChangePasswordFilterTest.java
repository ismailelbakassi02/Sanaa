package com.sanaa.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.sanaa.common.tenant.TenantContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared temporary-password rule. The filter is opt-in in every servlet service, so it must work
 * on its own with nothing but the validated token in the security context.
 */
class MustChangePasswordFilterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final MustChangePasswordFilter FILTER = new MustChangePasswordFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void blocksATemporarySessionOnEveryOtherEndpointWithAProblemDetail() throws Exception {
        authenticateWith(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/learner/courses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        FILTER.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        String body = response.getContentAsString();
        assertThat(body).doesNotContain("null", "Exception", "stack");
        JsonNode problem = JSON.readTree(body);
        assertThat(problem.get("type").asString()).isEqualTo("about:blank");
        assertThat(problem.get("title").asString()).isEqualTo("Forbidden");
        assertThat(problem.get("status").asInt()).isEqualTo(403);
        assertThat(problem.get("detail").asString()).isEqualTo("Password change required.");
        assertThat(problem.get("instance").asString()).isEqualTo("/api/learner/courses");
        assertThat(body).doesNotContain("tenant", "email");
    }

    @Test
    void allowsThePasswordChangeAndLogoutOnlyAsPosts() throws Exception {
        authenticateWith(true);

        MockFilterChain password = new MockFilterChain();
        FILTER.doFilter(new MockHttpServletRequest("POST", "/api/auth/password"),
                new MockHttpServletResponse(), password);
        assertThat(password.getRequest()).isNotNull();

        MockFilterChain logout = new MockFilterChain();
        FILTER.doFilter(new MockHttpServletRequest("POST", "/api/auth/logout"),
                new MockHttpServletResponse(), logout);
        assertThat(logout.getRequest()).isNotNull();

        // The same path with the wrong method carries a body in the real API; it must stay blocked.
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        FILTER.doFilter(new MockHttpServletRequest("GET", "/api/auth/password"), blocked,
                new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(403);
    }

    @Test
    void leavesNormalSessionsAndAnonymousRequestsAlone() throws Exception {
        authenticateWith(false);
        MockFilterChain normal = new MockFilterChain();
        FILTER.doFilter(new MockHttpServletRequest("GET", "/api/learner/courses"),
                new MockHttpServletResponse(), normal);
        assertThat(normal.getRequest()).isNotNull();

        SecurityContextHolder.clearContext();
        MockFilterChain anonymous = new MockFilterChain();
        FILTER.doFilter(new MockHttpServletRequest("GET", "/api/learner/courses"),
                new MockHttpServletResponse(), anonymous);
        assertThat(anonymous.getRequest()).isNotNull();
    }

    private static void authenticateWith(boolean mustChangePassword) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("42")
                .claim(JwtClaims.ROLE, "TENANT_ADMIN")
                .claim(JwtClaims.MUST_CHANGE_PASSWORD, mustChangePassword)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
