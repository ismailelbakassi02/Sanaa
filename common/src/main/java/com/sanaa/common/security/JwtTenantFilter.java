package com.sanaa.common.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sanaa.common.tenant.TenantContext;

/**
 * Copies the validated {@code tenant_id} claim into {@link TenantContext} for the current request.
 *
 * <p>WHY a filter and not a controller concern: the tenant must be present before a transaction or
 * Hibernate session opens, so it has to be set as soon as authentication is available.
 *
 * <p>WHY no {@code @Component}: auto-configuration creates the bean and disables Boot's plain
 * servlet registration, because the filter must only run after the bearer-token filter inside the
 * Security chain. Running it twice would be wasteful and could observe an empty SecurityContext.
 */
public class JwtTenantFilter extends OncePerRequestFilter {

    /**
     * Adds the filter after bearer-token authentication.
     *
     * <p>Usage in a service:
     * <pre>{@code
     * @Bean
     * SecurityFilterChain apiSecurity(HttpSecurity http, JwtTenantFilter tenantFilter) throws Exception {
     *     JwtTenantFilter.addTo(http, tenantFilter);
     *     return http.build();
     * }
     * }</pre>
     */
    public static void addTo(HttpSecurity http, JwtTenantFilter filter) throws Exception {
        http.addFilterAfter(filter, BearerTokenAuthenticationFilter.class);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Start from a clean slate: a pooled servlet thread may still carry a tenant from an
        // earlier request if any outer component skipped its cleanup.
        TenantContext.clear();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String tenantId = jwtAuthentication.getToken().getClaimAsString(JwtClaims.TENANT_ID);
            TenantContext.set(tenantId);
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }
        filterChain.doFilter(request, response);
    }
}
